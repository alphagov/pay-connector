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
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.client.ledger.model.Address;
import uk.gov.pay.connector.client.ledger.model.CardDetails;
import uk.gov.pay.connector.client.ledger.model.Exemption;
import uk.gov.pay.connector.client.ledger.model.ExemptionOutcome;
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
import java.util.Set;

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
import static uk.gov.pay.connector.tasks.service.ConnectAuthorisationSummaryState.CONNECTOR_HAS_REQUIRES_3DS_FALSE;
import static uk.gov.pay.connector.tasks.service.ConnectAuthorisationSummaryState.CONNECTOR_HAS_REQUIRES_3DS_NULL_AND_NO_3DS_REQUIRED_DETAILS;
import static uk.gov.pay.connector.tasks.service.ConnectAuthorisationSummaryState.CONNECTOR_HAS_REQUIRES_3DS_NULL_BUT_HAS_3DS_REQUIRED_DETAILS;
import static uk.gov.pay.connector.tasks.service.ConnectAuthorisationSummaryState.CONNECTOR_HAS_REQUIRES_3DS_TRUE;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionRequestedState.CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_CORPORATE;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionRequestedState.CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionRequestedState.CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_OPTIMISED;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionResultState.CONNECTOR_HAS_EXEMPTION_RESULT_HONOURED;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionResultState.CONNECTOR_HAS_EXEMPTION_RESULT_NOT_REQUESTED;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionResultState.CONNECTOR_HAS_EXEMPTION_RESULT_NULL;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionResultState.CONNECTOR_HAS_EXEMPTION_RESULT_OUT_OF_SCOPE;
import static uk.gov.pay.connector.tasks.service.Connector3dsExemptionResultState.CONNECTOR_HAS_EXEMPTION_RESULT_REJECTED;
import static uk.gov.pay.connector.tasks.service.LedgerAuthorisationSummaryState.LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_FALSE;
import static uk.gov.pay.connector.tasks.service.LedgerAuthorisationSummaryState.LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_TRUE;
import static uk.gov.pay.connector.tasks.service.LedgerAuthorisationSummaryState.LEDGER_HAS_NO_AUTHORISATION_SUMMARY;
import static uk.gov.pay.connector.tasks.service.LedgerAuthorisationSummaryState.LEDGER_HAS_SOMETHING_COMPLETELY_DIFFERENT;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_OUTCOME_WITH_NO_RESULT;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_OUTCOME_WITH_UNEXPECTED_RESULT;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_FALSE;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_AND_NO_OUTCOME;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_HONOURED;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_REJECTED;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_HONOURED;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_REJECTED;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_UNEXPECTED_TYPE_AND_OUTCOME_WITH_RESULT_HONOURED;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_UNEXPECTED_TYPE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_UNEXPECTED_TYPE_AND_OUTCOME_WITH_RESULT_REJECTED;
import static uk.gov.pay.connector.tasks.service.LedgerExemptionState.LEDGER_HAS_NO_EXEMPTION;
import static uk.gov.pay.connector.tasks.service.ParityCheckService.FIELD_NAME;
import static uk.gov.service.payments.commons.model.CommonDateTimeFormatters.ISO_INSTANT_MILLISECOND_PRECISION;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class ChargeParityChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(ChargeParityChecker.class);

    private static final Instant CHECK_AUTHORISATION_SUMMARY_PARITY_AFTER_DATE = Instant.parse("2021-09-01T00:00:00Z");
    
    private final RefundService refundService;
    private final PaymentProviders providers;

    record Exemption3dsStateCombination(
            Connector3dsExemptionRequestedState requestedState,
            Connector3dsExemptionResultState connectorState,
            LedgerExemptionState ledgerState) {}

    Set<Exemption3dsStateCombination> validCombinations = Set.of(
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL, CONNECTOR_HAS_EXEMPTION_RESULT_NULL, LEDGER_HAS_NO_EXEMPTION),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL, CONNECTOR_HAS_EXEMPTION_RESULT_NOT_REQUESTED, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_FALSE),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL, CONNECTOR_HAS_EXEMPTION_RESULT_HONOURED, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_HONOURED),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL, CONNECTOR_HAS_EXEMPTION_RESULT_REJECTED, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_REJECTED),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL, CONNECTOR_HAS_EXEMPTION_RESULT_OUT_OF_SCOPE, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_OPTIMISED, CONNECTOR_HAS_EXEMPTION_RESULT_NULL, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_AND_NO_OUTCOME),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_OPTIMISED, CONNECTOR_HAS_EXEMPTION_RESULT_HONOURED, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_HONOURED),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_OPTIMISED, CONNECTOR_HAS_EXEMPTION_RESULT_REJECTED, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_REJECTED),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_OPTIMISED, CONNECTOR_HAS_EXEMPTION_RESULT_OUT_OF_SCOPE, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_CORPORATE, CONNECTOR_HAS_EXEMPTION_RESULT_NULL, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_AND_NO_OUTCOME),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_CORPORATE, CONNECTOR_HAS_EXEMPTION_RESULT_HONOURED, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_HONOURED),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_CORPORATE, CONNECTOR_HAS_EXEMPTION_RESULT_REJECTED, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_REJECTED),
            new Exemption3dsStateCombination(CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_CORPORATE, CONNECTOR_HAS_EXEMPTION_RESULT_OUT_OF_SCOPE, LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE)
    );
    
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
            fieldsMatch = fieldsMatch && matchAuthorisationSummary(chargeEntity, transaction);
            fieldsMatch = fieldsMatch && matchExemption3dsFields(chargeEntity, transaction);
            if (!transaction.isDisputed()) {
                fieldsMatch = fieldsMatch && matchRefundSummary(chargeEntity, transaction);
            }

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
        fieldsMatch = fieldsMatch && isEquals(chargeEntity.getAgreementPaymentType(), transaction.getAgreementPaymentType(), "agreement_payment_type");

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

    private static ConnectAuthorisationSummaryState calculateConnectorAuthorisationSummaryState(ChargeEntity chargeEntity) {
        if (chargeEntity.getRequires3ds() == null) {
            if (chargeEntity.get3dsRequiredDetails() == null) {
                return CONNECTOR_HAS_REQUIRES_3DS_NULL_AND_NO_3DS_REQUIRED_DETAILS;
            } else {
                return CONNECTOR_HAS_REQUIRES_3DS_NULL_BUT_HAS_3DS_REQUIRED_DETAILS;
            }
        } else if (chargeEntity.getRequires3ds()) {
            return CONNECTOR_HAS_REQUIRES_3DS_TRUE;
        } else {
            return CONNECTOR_HAS_REQUIRES_3DS_FALSE;
        }
    }

    private LedgerAuthorisationSummaryState calculateLedgerAuthorisationSummaryState(LedgerTransaction transaction) {
        if (transaction.getAuthorisationSummary() == null) {
            return LEDGER_HAS_NO_AUTHORISATION_SUMMARY;
        } else {
            if (transaction.getAuthorisationSummary().getThreeDSecure() == null) {
                return LEDGER_HAS_SOMETHING_COMPLETELY_DIFFERENT;
            } else {
                if (transaction.getAuthorisationSummary().getThreeDSecure().isRequired()) {
                    return LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_TRUE;
                } else {
                    return LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_FALSE;
                }
            }
        }
    }

    private boolean matchAuthorisationSummary(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        if (chargeEntity.getCreatedDate().isBefore(CHECK_AUTHORISATION_SUMMARY_PARITY_AFTER_DATE)) {
            return true;
        }

        ConnectAuthorisationSummaryState connectorAuthorisationSummaryState = calculateConnectorAuthorisationSummaryState(chargeEntity);
        LedgerAuthorisationSummaryState ledgerAuthorisationSummaryState = calculateLedgerAuthorisationSummaryState(transaction);

        return switch (connectorAuthorisationSummaryState) {
            case CONNECTOR_HAS_REQUIRES_3DS_NULL_AND_NO_3DS_REQUIRED_DETAILS -> {
                if (ledgerAuthorisationSummaryState != LEDGER_HAS_NO_AUTHORISATION_SUMMARY) {
                    logger.info("Field value does not match between ledger and connector [field_name={}]", "authorisation_summary.three_d_secure.required",
                            kv(FIELD_NAME, "authorisation_summary.three_d_secure.required"));
                    yield false;
                }
                yield true;
            }
            case CONNECTOR_HAS_REQUIRES_3DS_FALSE -> {
                if (ledgerAuthorisationSummaryState != LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_FALSE) {
                    logger.info("Field value does not match between ledger and connector [field_name={}]",
                            "authorisation_summary.three_d_secure.required",
                            kv(FIELD_NAME, "authorisation_summary.three_d_secure.required"));
                    yield false;
                }
                yield compareVersions(chargeEntity, transaction);
            }

            case CONNECTOR_HAS_REQUIRES_3DS_TRUE, CONNECTOR_HAS_REQUIRES_3DS_NULL_BUT_HAS_3DS_REQUIRED_DETAILS -> {
                if (ledgerAuthorisationSummaryState != LEDGER_HAS_AUTHORISATION_SUMMARY_WITH_THREE_D_S_REQUIRED_TRUE) {
                    logger.info("Field value does not match between ledger and connector [field_name={}]", "authorisation_summary.three_d_secure.required",
                            kv(FIELD_NAME, "authorisation_summary.three_d_secure.required"));
                    yield false;
                }
                yield compareVersions(chargeEntity, transaction);
            }
        };
    }

    private static Connector3dsExemptionRequestedState calculateConnectorExemption3dsRequested(ChargeEntity chargeEntity) {
        return switch (chargeEntity.getExemption3dsRequested()) {
            case null -> CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_NULL;
            case OPTIMISED -> CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_OPTIMISED;
            case CORPORATE -> CONNECTOR_HAS_EXEMPTION_3DS_REQUESTED_CORPORATE;
        };
    }
    
    private static Connector3dsExemptionResultState calculateConnectorExemption3ds(ChargeEntity chargeEntity) {
        return switch (chargeEntity.getExemption3ds()) {
            case null -> CONNECTOR_HAS_EXEMPTION_RESULT_NULL;
            case EXEMPTION_HONOURED -> CONNECTOR_HAS_EXEMPTION_RESULT_HONOURED;
            case EXEMPTION_REJECTED -> CONNECTOR_HAS_EXEMPTION_RESULT_REJECTED;
            case EXEMPTION_NOT_REQUESTED -> CONNECTOR_HAS_EXEMPTION_RESULT_NOT_REQUESTED;
            case EXEMPTION_OUT_OF_SCOPE -> CONNECTOR_HAS_EXEMPTION_RESULT_OUT_OF_SCOPE;
        };
    }

    private static LedgerExemptionState calculateLedgerExemptionState(LedgerTransaction transaction) {
        return switch (transaction.getExemption()) {
            case null -> LEDGER_HAS_NO_EXEMPTION;
            case Exemption(boolean requested, String type, ExemptionOutcome outcome) when !requested -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_FALSE;
            case Exemption(boolean requested, String type, ExemptionOutcome outcome) when outcome == null -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_AND_NO_OUTCOME;
            case Exemption(boolean requested, String type, ExemptionOutcome(String result)) -> switch (result) {
                case null -> LEDGER_HAS_EXEMPTION_WITH_OUTCOME_WITH_NO_RESULT;   
                case "honoured" -> switch (type) {
                    case null -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_HONOURED;
                    case "corporate" -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_HONOURED;
                    default -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_UNEXPECTED_TYPE_AND_OUTCOME_WITH_RESULT_HONOURED;
                };
                case "rejected" -> switch (type) {
                    case null -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_REJECTED;
                    case "corporate" -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_REJECTED;
                    default -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_UNEXPECTED_TYPE_AND_OUTCOME_WITH_RESULT_REJECTED;
                };
                case "out of scope" -> switch (type) {
                    case null -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_NO_TYPE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE;
                    case "corporate" -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_TYPE_CORPORATE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE;
                    default -> LEDGER_HAS_EXEMPTION_WITH_REQUESTED_TRUE_UNEXPECTED_TYPE_AND_OUTCOME_WITH_RESULT_OUT_OF_SCOPE;
                };
                default -> LEDGER_HAS_EXEMPTION_WITH_OUTCOME_WITH_UNEXPECTED_RESULT;
            };
        };
    }

    private boolean matchExemption3dsFields(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        Connector3dsExemptionResultState connectorExemption3dsState = calculateConnectorExemption3ds(chargeEntity);
        Connector3dsExemptionRequestedState connectorExemption3DsRequestedState = calculateConnectorExemption3dsRequested(chargeEntity);
        LedgerExemptionState ledgerExemptionState = calculateLedgerExemptionState(transaction);
        return validCombinations.contains(new Exemption3dsStateCombination(connectorExemption3DsRequestedState, connectorExemption3dsState, ledgerExemptionState));
    }

    private boolean compareVersions(ChargeEntity chargeEntity, LedgerTransaction transaction) {
        String connectorVersion = Optional.ofNullable(chargeEntity.get3dsRequiredDetails())
                .map(Auth3dsRequiredEntity::getThreeDsVersion)
                .orElse(null);
        String ledgerVersion = transaction.getAuthorisationSummary().getThreeDSecure().getVersion();
        return isEquals(connectorVersion, ledgerVersion, "authorisation_summary.three_d_secure.version");
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
