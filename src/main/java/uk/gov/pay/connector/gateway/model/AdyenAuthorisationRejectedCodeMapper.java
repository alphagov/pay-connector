package uk.gov.pay.connector.gateway.model;

import java.util.Map;

import static java.util.Map.entry;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.AUTHENTICATION_REQUIRED;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.AUTHENTICATION_REQUESTED;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.CARD_BLOCKED_OR_INVALID_OR_NONEXISTENT;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.DO_NOT_HONOUR;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.DO_NOT_RETRY;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.EXPIRED_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.GENERIC_DECLINE;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INSUFFICIENT_FUNDS;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_ACCOUNT_NUMBER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_AMOUNT;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_CVV2;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_MERCHANT;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.ISSUER_TEMPORARILY_UNAVAILABLE;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.REFER_TO_CARD_ISSUER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.REVOCATION_OF_AUTHORISATION;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.RESTRICTED_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.SUSPECTED_FRAUD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.TRANSACTION_NOT_PERMITTED;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.TRY_AGAIN_LATER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.TRY_ANOTHER_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.UNCATEGORISED;

public class AdyenAuthorisationRejectedCodeMapper {
    
    private static final Map<String, MappedAuthorisationRejectedReason> ADYEN_REJECTION_CODE_MAP = Map.ofEntries(
            entry("0", UNCATEGORISED),
            entry("2", DO_NOT_HONOUR),
            entry("3", REFER_TO_CARD_ISSUER),
            entry("4", ISSUER_TEMPORARILY_UNAVAILABLE),
            entry("5", CARD_BLOCKED_OR_INVALID_OR_NONEXISTENT),
            entry("6", EXPIRED_CARD),
            entry("7", INVALID_AMOUNT),
            entry("8", INVALID_ACCOUNT_NUMBER),
            entry("9", ISSUER_TEMPORARILY_UNAVAILABLE),
            entry("10", INVALID_MERCHANT),
            entry("11", AUTHENTICATION_REQUESTED),
            entry("12", INSUFFICIENT_FUNDS),
            entry("14", SUSPECTED_FRAUD),
            entry("15", DO_NOT_RETRY),
            entry("16", DO_NOT_RETRY),
            entry("17", INVALID_CVV2),
            entry("18", EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT),
            entry("22", SUSPECTED_FRAUD),
            entry("23", TRANSACTION_NOT_PERMITTED),
            entry("24", INVALID_CVV2),
            entry("25", RESTRICTED_CARD),
            entry("26", REVOCATION_OF_AUTHORISATION),
            entry("27", GENERIC_DECLINE),
            entry("28", EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT),
            entry("29", EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT),
            entry("32", INVALID_CVV2),
            entry("33", AUTHENTICATION_REQUESTED),
            entry("34", CARD_BLOCKED_OR_INVALID_OR_NONEXISTENT),
            entry("35", CARD_BLOCKED_OR_INVALID_OR_NONEXISTENT),
            entry("36", AUTHENTICATION_REQUESTED),
            entry("37", TRY_AGAIN_LATER),
            entry("38", AUTHENTICATION_REQUIRED),
            entry("39", TRY_AGAIN_LATER),
            entry("40", TRY_AGAIN_LATER),
            entry("41", TRY_AGAIN_LATER),
            entry("42", AUTHENTICATION_REQUESTED),
            entry("43", AUTHENTICATION_REQUESTED),
            entry("44", TRY_ANOTHER_CARD),
            entry("45", TRY_AGAIN_LATER),
            entry("46", DO_NOT_RETRY)
            );

    public static MappedAuthorisationRejectedReason toMappedAuthorisationRejectionReason(String adyenRefusalReasonCode) {
        return ADYEN_REJECTION_CODE_MAP.getOrDefault(adyenRefusalReasonCode, UNCATEGORISED);
    }

}
