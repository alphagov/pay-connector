package uk.gov.pay.connector.gateway.model;

import java.util.Map;

import static java.util.Map.entry;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.AUTHENTICATION_REQUESTED;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.CAPTURE_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.CARD_BLOCKED_OR_INVALID_OR_NONEXISTENT;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.CLOSED_ACCOUNT;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.DO_NOT_HONOUR;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.EXPIRED_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INSUFFICIENT_FUNDS;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_ACCOUNT_NUMBER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_AMOUNT;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_CARD_NUMBER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_CVV2;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_MERCHANT;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_TRANSACTION;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.ISSUER_TEMPORARILY_UNAVAILABLE;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.LOST_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.MASTERCARD_FRAUD_SECURITY_REASONS_OR_VISA_STIP_CANNOT_APPROVE;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.MASTERCARD_LIFECYCLE_REASONS_OR_VISA_ALREADY_REVERSED;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.MASTERCARD_POLICY_REASONS_OR_VISA_NEGATIVE_CAM_DCVV_ICVV_OR_CVV_RESULTS;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.NO_SUCH_ISSUER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.PICKUP_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.REFER_TO_CARD_ISSUER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.RESTRICTED_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.REVOCATION_OF_ALL_AUTHORISATION;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.REVOCATION_OF_AUTHORISATION;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.STOLEN_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.STOP_PAYMENT_ORDER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.SUSPECTED_FRAUD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.TRANSACTION_NOT_PERMITTED;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.TRY_ANOTHER_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.UNCATEGORISED;

public class WorldpayAuthorisationRejectedCodeMapper {

    private static final Map<String, MappedAuthorisationRejectedReason> WORLDPAY_REJECTION_CODE_MAP = Map.ofEntries(
                entry("1", REFER_TO_CARD_ISSUER),
                entry("3", INVALID_MERCHANT),
                entry("4", CAPTURE_CARD),
                entry("5", DO_NOT_HONOUR),
                entry("6", TRY_ANOTHER_CARD),
                entry("7", PICKUP_CARD),
                entry("12", INVALID_TRANSACTION),
                entry("13", INVALID_AMOUNT),
                entry("14", INVALID_CARD_NUMBER),
                entry("15", NO_SUCH_ISSUER),
                entry("34", SUSPECTED_FRAUD),
                entry("41", LOST_CARD),
                entry("43", STOLEN_CARD),
                entry("46", CLOSED_ACCOUNT),
                entry("51", INSUFFICIENT_FUNDS),
                entry("54", EXPIRED_CARD),
                entry("57", TRANSACTION_NOT_PERMITTED),
                entry("59", SUSPECTED_FRAUD),
                entry("61", EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT),
                entry("62", RESTRICTED_CARD),
                entry("65", AUTHENTICATION_REQUESTED),
                entry("76", CARD_BLOCKED_OR_INVALID_OR_NONEXISTENT),
                entry("78", INVALID_ACCOUNT_NUMBER),
                entry("79", MASTERCARD_LIFECYCLE_REASONS_OR_VISA_ALREADY_REVERSED),
                entry("82", MASTERCARD_POLICY_REASONS_OR_VISA_NEGATIVE_CAM_DCVV_ICVV_OR_CVV_RESULTS),
                entry("83", MASTERCARD_FRAUD_SECURITY_REASONS_OR_VISA_STIP_CANNOT_APPROVE),
                entry("91", ISSUER_TEMPORARILY_UNAVAILABLE),
                entry("835", INVALID_CVV2),
                entry("972", STOP_PAYMENT_ORDER),
                entry("973", REVOCATION_OF_AUTHORISATION),
                entry("975", REVOCATION_OF_ALL_AUTHORISATION));

    public static MappedAuthorisationRejectedReason toMappedAuthorisationRejectionReason(String worldpayRejectionCode) {
        return WORLDPAY_REJECTION_CODE_MAP.getOrDefault(worldpayRejectionCode, UNCATEGORISED);
    }

}
