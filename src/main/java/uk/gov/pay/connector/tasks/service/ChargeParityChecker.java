package uk.gov.pay.connector.tasks.service;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.Address;
import uk.gov.pay.connector.client.ledger.model.CardDetails;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.SettlementSummary;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.service.RefundService;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.time.Duration;
import java.time.Instant;
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
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.DATA_MISMATCH;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.EXISTS_IN_LEDGER;
import static uk.gov.pay.connector.charge.model.domain.ParityCheckStatus.MISSING_IN_LEDGER;
import static uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator.getTotalAmountFor;
import static uk.gov.pay.connector.tasks.service.ParityCheckService.FIELD_NAME;
import static uk.gov.service.payments.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class ChargeParityChecker {

    private static final Logger logger = LoggerFactory.getLogger(ChargeParityChecker.class);

    private static final Instant CHECK_AUTHORISATION_SUMMARY_PARITY_AFTER_DATE = Instant.parse("2021-09-01T00:00:00Z");

    private final RefundService refundService;
    private final PaymentProviders providers;

    @Inject
    public ChargeParityChecker(RefundService refundService, PaymentProviders providers) {
        this.refundService = refundService;
        this.providers = providers;
    }

    public ParityCheckStatus checkParity(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        String externalId = chargeEntity.getExternalId();
        ParityCheckStatus parityCheckStatus;

        MDC.put(PAYMENT_EXTERNAL_ID, externalId);

        if (transaction == null) {
            logger.info("Transaction missing in Ledger for Charge [external_id={}]", chargeEntity.getExternalId());
            parityCheckStatus = MISSING_IN_LEDGER;
        } else {
            boolean fieldsMatch;

            fieldsMatch = matchCommonFields(chargeEntity, transaction);
            fieldsMatch = fieldsMatch && matchCreatedDate(chargeEntity, transaction);
            fieldsMatch = fieldsMatch && matchCardDetails(chargeEntity.getCardDetails(), transaction.getCardDetails());
            fieldsMatch = fieldsMatch && matchGatewayAccountFields(chargeEntity.getGatewayAccount(), transaction);
            fieldsMatch = fieldsMatch && matchFeatureSpecificFields(chargeEntity, transaction);
            fieldsMatch = fieldsMatch && matchCaptureFields(chargeEntity, transaction);
            if (!transaction.isDisputed()) {
                fieldsMatch = fieldsMatch && matchRefundSummary(chargeEntity, transaction);
            }
            fieldsMatch = fieldsMatch && matchAuthorisationSummary(chargeEntity, transaction);

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
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getPaymentProvider(), transaction.getPaymentProvider(), "payment_provider");

        // email may be empty in connector but not in ledger, if service provides email but turns off email address collection
        fieldsMatch = fieldsMatch && (
                (chargeEntity.getEmail() == null && transaction.getEmail() != null)
                        || isEquals(chargeEntity.getEmail(), transaction.getEmail(), "email")
        );

        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getReturnUrl(), transaction.getReturnUrl(), "return_url");
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getGatewayTransactionId(), transaction.getGatewayTransactionId(), "gateway_transaction_id");

        String chargeExternalStatus = ChargeStatus.fromString(chargeEntity.getStatus()).toExternal().getStatusV2();
        fieldsMatch = fieldsMatch && isEquals(chargeExternalStatus, transaction.getState().getStatus(), "status");

        return fieldsMatch;
    }

    private boolean matchCreatedDate(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        boolean createdDateMatches = getChargeEventDate(chargeEntity, List.of(CREATED, PAYMENT_NOTIFICATION_CREATED))
                .map(connectorDate -> {
                    // Due to charge events being out of order for some historic payments, the date in ledger was 
                    // slightly different to in connector. Allow for a few seconds of difference as it is not worth 
                    // correcting this minor disparity.
                    return Math.abs(Duration.between(connectorDate, ZonedDateTime.parse(transaction.getCreatedDate())).toMillis()) < 5000;
                })
                .orElse(false);

        if (!createdDateMatches) {
            logger.info("Field value does not match between ledger and connector [field_name=created_date]",
                    kv(FIELD_NAME, "created_date"));
        }
        return createdDateMatches;
    }

    private boolean matchCardDetails(CardDetailsEntity cardDetailsEntity, CardDetails ledgerCardDetails) {
        boolean fieldsMatch;

        if (isNull(cardDetailsEntity)) {
            return true;
        }

        if (nonNull(ledgerCardDetails)) {

            // card holder name may be empty in connector but not in ledger, if service provides name but payment is made using wallet
            fieldsMatch = (
                    (cardDetailsEntity.getCardHolderName() == null && ledgerCardDetails.getCardholderName() != null)
                            || isEquals(cardDetailsEntity.getCardHolderName(), ledgerCardDetails.getCardholderName(), "cardholder_name")
            );

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
            fieldsMatch = fieldsMatch && isEquals(
                    Optional.ofNullable(cardDetailsEntity.getExpiryDate()).map(CardExpiryDate::toString).orElse(null),
                    ledgerCardDetails.getExpiryDate(), "expiry_date");

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

        // address details can be empty in connector but not in ledger if service provides billing address
        // but turned off settings to collect billing address or for wallet payment
        if ((isNull(addressEntity) && isNull(ledgerBillingAddress)) || isNull(addressEntity)) {
            return true;
        }

        if (isNull(ledgerBillingAddress)) {
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

        if (chargeEntity.getCorporateSurcharge().isPresent()) {
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
        List<Refund> refundList = refundService.findRefunds(Charge.from(chargeEntity));

        ExternalChargeRefundAvailability refundAvailability = providers.byName(chargeEntity.getPaymentGatewayName())
                .getExternalChargeRefundAvailability(Charge.from(chargeEntity), refundList);

        return isEquals(refundAvailability.getStatus(),
                ofNullable(transaction.getRefundSummary()).map(ChargeResponse.RefundSummary::getStatus).orElse(null), "refund_summary.status");
    }

    private boolean matchAuthorisationSummary(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        if (chargeEntity.getCreatedDate().isBefore(CHECK_AUTHORISATION_SUMMARY_PARITY_AFTER_DATE)) {
            return true;
        }
        if (chargeEntity.get3dsRequiredDetails() == null) {
            if (transaction.getAuthorisationSummary() == null) {
                return true;
            }
            logger.info("Field value does not match between ledger and connector [field_name={}]", "authorisation_summary",
                    kv(FIELD_NAME, "authorisation_summary"));
            return false;
        }

        if (transaction.getAuthorisationSummary() == null
                || transaction.getAuthorisationSummary().getThreeDSecure() == null
                || !transaction.getAuthorisationSummary().getThreeDSecure().isRequired()) {
            logger.info("Field value does not match between ledger and connector [field_name={}]", "authorisation_summary.three_d_secure.required",
                    kv(FIELD_NAME, "authorisation_summary.three_d_secure.required"));
            return false;
        }

        return isEquals(chargeEntity.get3dsRequiredDetails().getThreeDsVersion(),
                transaction.getAuthorisationSummary().getThreeDSecure().getVersion(), "authorisation_summary.three_d_secure.version");
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
