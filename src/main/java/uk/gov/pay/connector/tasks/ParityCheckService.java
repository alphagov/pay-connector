package uk.gov.pay.connector.tasks;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.Address;
import uk.gov.pay.connector.client.ledger.model.CardDetails;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.SettlementSummary;
import uk.gov.pay.connector.client.ledger.service.LedgerService;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.wallets.WalletType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static uk.gov.pay.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.DATA_MISMATCH;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.MISSING_IN_LEDGER;
import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getTotalAmountFor;
import static uk.gov.pay.connector.tasks.HistoricalEventEmitter.TERMINAL_AUTHENTICATION_STATES;
import static uk.gov.pay.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class ParityCheckService {

    public static final String FIELD_NAME = "field_name";
    private static final Logger logger = LoggerFactory.getLogger(ParityCheckService.class);
    private LedgerService ledgerService;
    private ChargeService chargeService;
    private RefundDao refundDao;
    private HistoricalEventEmitter historicalEventEmitter;
    private final PaymentProviders providers;

    @Inject
    public ParityCheckService(LedgerService ledgerService, ChargeService chargeService,
                              RefundDao refundDao,
                              HistoricalEventEmitter historicalEventEmitter,
                              PaymentProviders providers) {
        this.ledgerService = ledgerService;
        this.chargeService = chargeService;
        this.refundDao = refundDao;
        this.historicalEventEmitter = historicalEventEmitter;
        this.providers = providers;
    }

    public ParityCheckStatus getChargeParityCheckStatus(ChargeEntity chargeEntity) {
        Optional<LedgerTransaction> transaction = ledgerService.getTransaction(chargeEntity.getExternalId());

        return checkParity(chargeEntity, transaction.orElse(null));
    }

    public ParityCheckStatus getChargeAndRefundsParityCheckStatus(ChargeEntity charge) {
        ParityCheckStatus parityCheckStatus = getChargeParityCheckStatus(charge);
        if (parityCheckStatus.equals(EXISTS_IN_LEDGER)) {
            return getRefundsParityCheckStatus(refundDao.findRefundsByChargeExternalId(charge.getExternalId()));
        }

        return parityCheckStatus;
    }

    public ParityCheckStatus getParityCheckStatus(Optional<LedgerTransaction> transaction, String externalChargeState) {
        if (transaction.isEmpty()) {
            return MISSING_IN_LEDGER;
        }

        if (externalChargeState.equalsIgnoreCase(transaction.get().getState().getStatus())) {
            return EXISTS_IN_LEDGER;
        }

        return DATA_MISMATCH;
    }


    public ParityCheckStatus getRefundsParityCheckStatus(List<RefundEntity> refunds) {
        for (var refund : refunds) {
            var transaction = ledgerService.getTransaction(refund.getExternalId());
            ParityCheckStatus parityCheckStatus = getParityCheckStatus(transaction, refund.getStatus().toExternal().getStatus());
            if (!parityCheckStatus.equals(EXISTS_IN_LEDGER)) {
                logger.info("refund transaction does not exist in ledger or is in a different state [externalId={},status={}] -",
                        refund.getExternalId(), parityCheckStatus);
                return parityCheckStatus;
            }
        }

        return EXISTS_IN_LEDGER;
    }

    @Transactional
    public boolean parityCheckChargeForExpunger(ChargeEntity chargeEntity) {
        ParityCheckStatus parityCheckStatus = getChargeParityCheckStatus(chargeEntity);

        if (EXISTS_IN_LEDGER.equals(parityCheckStatus)) {
            return true;
        }

        // force emit and update charge status
        historicalEventEmitter.processPaymentEvents(chargeEntity, true);
        chargeService.updateChargeParityStatus(chargeEntity.getExternalId(), parityCheckStatus);

        return false;
    }

    private ParityCheckStatus checkParity(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        String externalId = chargeEntity.getExternalId();
        ParityCheckStatus parityCheckStatus;

        MDC.put(PAYMENT_EXTERNAL_ID, externalId);

        if (transaction == null) {
            logger.info("Transaction missing in Ledger for Charge [external_id={}]", chargeEntity.getExternalId());
            parityCheckStatus = MISSING_IN_LEDGER;
        } else {
            boolean fieldsMatch;

            fieldsMatch = matchCommonFields(chargeEntity, transaction);
            fieldsMatch = fieldsMatch && matchCardDetails(chargeEntity.getCardDetails(), transaction.getCardDetails());
            fieldsMatch = fieldsMatch && matchGatewayAccountFields(chargeEntity.getGatewayAccount(), transaction);
            fieldsMatch = fieldsMatch && matchFeatureSpecificFields(chargeEntity, transaction);
            fieldsMatch = fieldsMatch && matchCaptureFields(chargeEntity, transaction);
            fieldsMatch = fieldsMatch && matchRefundSummary(chargeEntity, transaction);

            if (fieldsMatch) {
                parityCheckStatus = EXISTS_IN_LEDGER;
            } else {
                parityCheckStatus = DATA_MISMATCH;
            }
        }
        MDC.remove(PAYMENT_EXTERNAL_ID);
        return parityCheckStatus;
    }

    private boolean matchCommonFields(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        boolean fieldsMatch = isEquals(chargeEntity.getExternalId(), transaction.getTransactionId(), "external_id");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getAmount(), transaction.getAmount(), "amount");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getDescription(), transaction.getDescription(), "description");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getReference().toString(), transaction.getReference(), "reference");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getLanguage(), transaction.getLanguage(), "language");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getEmail(), transaction.getEmail(), "email");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getReturnUrl(), transaction.getReturnUrl(), "return_url");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getGatewayTransactionId(), transaction.getGatewayTransactionId(), "gateway_transaction_id");
        fieldsMatch = fieldsMatch && isEquals(
                getChargeEventDate(chargeEntity, List.of(CREATED, PAYMENT_NOTIFICATION_CREATED))
                        .map(ISO_INSTANT_MILLISECOND_PRECISION::format).orElse(null),
                transaction.getCreatedDate(), "created_date");

        String chargeExternalStatus = ChargeStatus.fromString(chargeEntity.getStatus()).toExternal().getStatusV2();
        fieldsMatch = fieldsMatch && isEquals(chargeExternalStatus, transaction.getState().getStatus(), "status");

        return fieldsMatch;
    }

    private boolean matchCardDetails(CardDetailsEntity cardDetailsEntity, CardDetails ledgerCardDetails) {
        boolean fieldsMatch;

        if (isNull(cardDetailsEntity)) {
            return true;
        }

        if (nonNull(ledgerCardDetails)) {
            fieldsMatch = isEquals(cardDetailsEntity.getCardHolderName(), ledgerCardDetails.getCardholderName(), "cardholder_name");
            fieldsMatch = fieldsMatch && isEquals(
                    ofNullable(cardDetailsEntity.getLastDigitsCardNumber()).map(LastDigitsCardNumber::toString).orElse(null),
                    ledgerCardDetails.getLastDigitsCardNumber(), "last_digits_card_number");
            fieldsMatch = fieldsMatch && isEquals(
                    ofNullable(cardDetailsEntity.getFirstDigitsCardNumber()).map(FirstDigitsCardNumber::toString).orElse(null),
                    ledgerCardDetails.getFirstDigitsCardNumber(), "first_digits_card_number");
            fieldsMatch = fieldsMatch && isEquals(
                    cardDetailsEntity.getCardTypeDetails().map(CardBrandLabelEntity::getLabel).orElse(null),
                    isEmpty(ledgerCardDetails.getCardBrand()) ? null : ledgerCardDetails.getCardBrand(),
                    "card_brand");
            fieldsMatch = fieldsMatch && isEquals(cardDetailsEntity.getExpiryDate(), ledgerCardDetails.getExpiryDate(), "expiry_date");

            String cardType = null;
            if (cardDetailsEntity.getCardType() != null) {
                cardType = cardDetailsEntity.getCardType().toString().toLowerCase();
            }

            fieldsMatch = fieldsMatch && isEquals(
                    cardType,
                    ledgerCardDetails.getCardType(), "card_type");

            fieldsMatch = fieldsMatch && matchBillingAddress(cardDetailsEntity.getBillingAddress().orElse(null), ledgerCardDetails.getBillingAddress());

            return fieldsMatch;
        } else {
            logger.info("Field value does not match between ledger and connector [field_name={}]", "card_details",
                    kv(FIELD_NAME, "card_details"));
            return false;
        }
    }

    private boolean matchBillingAddress(AddressEntity addressEntity, Address ledgerBillingAddress) {

        boolean fieldsMatch;

        if (isNull(addressEntity) && isNull(ledgerBillingAddress)) {
            return true;
        }

        if (nonNull(addressEntity) && isNull(ledgerBillingAddress) || isNull(addressEntity)) {
            logger.info("Field value does not match between ledger and connector [field_name={}]", "billing_address",
                    kv(FIELD_NAME, "billing_address"));
            fieldsMatch = false;
        } else {
            fieldsMatch = isEquals(addressEntity.getLine1(), ledgerBillingAddress.getLine1(), "line_1");
            fieldsMatch = fieldsMatch && isEquals(addressEntity.getLine2(), ledgerBillingAddress.getLine2(), "line_2");
            fieldsMatch = fieldsMatch && isEquals(addressEntity.getCity(), ledgerBillingAddress.getCity(), "city");
            fieldsMatch = fieldsMatch && isEquals(addressEntity.getCounty(), ledgerBillingAddress.getCounty(), "county");
            fieldsMatch = fieldsMatch && isEquals(addressEntity.getCountry(), ledgerBillingAddress.getCountry(), "country");
            fieldsMatch = fieldsMatch && isEquals(addressEntity.getPostcode(), ledgerBillingAddress.getPostcode(), "post_code");
        }

        return fieldsMatch;
    }

    private boolean matchGatewayAccountFields(GatewayAccountEntity gatewayAccount, LedgerTransaction transaction) {
        boolean fieldsMatch = isEquals(gatewayAccount.getId().toString(), transaction.getGatewayAccountId(), "gateway_account_id");
        fieldsMatch = fieldsMatch && isEquals(gatewayAccount.getGatewayName(), transaction.getPaymentProvider(), "payment_provider");
        fieldsMatch = fieldsMatch && isEquals(gatewayAccount.isLive(), transaction.getLive(), "live");
        return fieldsMatch;
    }

    private boolean matchFeatureSpecificFields(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        boolean fieldsMatch = isEquals(chargeEntity.isDelayedCapture(), transaction.getDelayedCapture(), "delayed_capture");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getSource(), transaction.getSource(), "source");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.isMoto(), transaction.isMoto(), "moto");

        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getFeeAmount().orElse(null),
                transaction.getFee(), "fee");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getCorporateSurcharge().orElse(null),
                transaction.getCorporateCardSurcharge(), "corporate_surcharge");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getNetAmount().orElse(null),
                transaction.getNetAmount(), "net_amount");

        if (hasTerminalAuthenticationState(chargeEntity.getEvents())) {
            fieldsMatch = fieldsMatch && isEquals(getTotalAmountFor(chargeEntity),
                    transaction.getTotalAmount(), "total_amount");
        }

        fieldsMatch = fieldsMatch &&
                isEquals(ofNullable(chargeEntity.getWalletType())
                        .map(WalletType::toString)
                        .orElse(null), transaction.getWalletType(), "wallet_type");

        fieldsMatch = fieldsMatch && externalMetadataMatches(chargeEntity, transaction);

        return fieldsMatch;
    }

    private boolean hasTerminalAuthenticationState(List<ChargeEventEntity> eventEntities) {
        Optional<ChargeEventEntity> mayBeChargeEventEntity = eventEntities.stream()
                .filter(chargeEventEntity -> TERMINAL_AUTHENTICATION_STATES.contains(chargeEventEntity.getStatus()))
                .findFirst();
        return mayBeChargeEventEntity.isPresent();
    }

    private boolean externalMetadataMatches(ChargeEntity chargeEntity,
                                            LedgerTransaction transaction) {
        Optional<ExternalMetadata> chargeExternalMetadata = chargeEntity.getExternalMetadata();
        Map<String, Object> ledgerExternalMetaData = transaction.getExternalMetaData();
        if (chargeExternalMetadata.isEmpty() && isNull(ledgerExternalMetaData)) {
            return true;
        }

        return (chargeExternalMetadata.isEmpty() || (nonNull(ledgerExternalMetaData) && ledgerExternalMetaData.size() > 0))
                && (chargeExternalMetadata.isPresent() && chargeExternalMetadata.get().getMetadata().size() > 0);
    }

    private boolean matchCaptureFields(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        boolean fieldsMatch = isEquals(
                getChargeEventDate(chargeEntity, List.of(CAPTURED)).map(DateTimeUtils::toUTCDateString).orElse(null),
                ofNullable(transaction.getSettlementSummary()).map(SettlementSummary::getCapturedDate).orElse(null),
                "captured_date");
        fieldsMatch &= isEquals(
                getChargeEventDate(chargeEntity, List.of(CAPTURE_SUBMITTED)).map(ISO_INSTANT_MILLISECOND_PRECISION::format).orElse(null),
                ofNullable(transaction.getSettlementSummary()).map(SettlementSummary::getCaptureSubmitTime).orElse(null),
                "capture_submit_time");

        return fieldsMatch;
    }

    private boolean matchRefundSummary(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        List<RefundEntity> refundsList = refundDao.findRefundsByChargeExternalId(chargeEntity.getExternalId());

        ExternalChargeRefundAvailability refundAvailability = providers.byName(chargeEntity.getPaymentGatewayName())
                .getExternalChargeRefundAvailability(Charge.from(chargeEntity), refundsList);

        return isEquals(refundAvailability.getStatus(),
                ofNullable(transaction.getRefundSummary()).map(refundSummary -> refundSummary.getStatus()).orElse(null), "refund_summary.status");
    }

    private Optional<ZonedDateTime> getChargeEventDate(ChargeEntity chargeEntity, List<ChargeStatus> chargeEventStatuses) {
        return chargeEntity.getEvents()
                .stream()
                .filter(chargeEventEntity -> chargeEventStatuses.contains(chargeEventEntity.getStatus()))
                .findFirst()
                .map(ChargeEventEntity::getUpdated);
    }

    private boolean isEquals(Object value1, Object value2, String fieldName) {
        if (Objects.equals(value1, value2)) {
            return true;
        } else {
            logger.info("Field value does not match between ledger and connector [field_name={}]", fieldName,
                    kv(FIELD_NAME, fieldName));
            return false;
        }
    }
}
