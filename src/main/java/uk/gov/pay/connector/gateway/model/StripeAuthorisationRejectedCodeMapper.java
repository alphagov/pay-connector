package uk.gov.pay.connector.gateway.model;

import java.util.Map;

import static java.util.Map.entry;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.AUTHENTICATION_REQUIRED;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.DO_NOT_HONOUR;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.DO_NOT_RETRY;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.EXPIRED_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.GENERIC_DECLINE;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INCORRECT_NUMBER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INSUFFICIENT_FUNDS;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_ACCOUNT_NUMBER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.INVALID_AMOUNT;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.ISSUER_TEMPORARILY_UNAVAILABLE;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.LOST_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.PICKUP_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.REENTER_TRANSACTION;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.REVOCATION_OF_ALL_AUTHORISATION;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.REVOCATION_OF_AUTHORISATION;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.STOLEN_CARD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.STOP_PAYMENT_ORDER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.SUSPECTED_FRAUD;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.TRANSACTION_NOT_PERMITTED;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.TRY_AGAIN_LATER;
import static uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason.UNCATEGORISED;

public class StripeAuthorisationRejectedCodeMapper {
    
    private static final Map<String, MappedAuthorisationRejectedReason> STRIPE_REJECTION_CODE_MAP = Map.ofEntries(
            entry("authentication_required", AUTHENTICATION_REQUIRED),
            entry("do_not_honor", DO_NOT_HONOUR),
            entry("do_not_try_again", DO_NOT_RETRY),
            entry("expired_card", EXPIRED_CARD),
            entry("fraudulent", SUSPECTED_FRAUD),
            entry("generic_decline", GENERIC_DECLINE),
            entry("incorrect_number", INCORRECT_NUMBER),
            entry("insufficient_funds", INSUFFICIENT_FUNDS),
            entry("invalid_account", INVALID_ACCOUNT_NUMBER),
            entry("invalid_amount", INVALID_AMOUNT),
            entry("issuer_not_available", ISSUER_TEMPORARILY_UNAVAILABLE),
            entry("lost_card", LOST_CARD),
            entry("pickup_card", PICKUP_CARD),
            entry("reenter_transaction", REENTER_TRANSACTION),
            entry("revocation_of_all_authorizations", REVOCATION_OF_ALL_AUTHORISATION),
            entry("revocation_of_authorization", REVOCATION_OF_AUTHORISATION),
            entry("stolen_card", STOLEN_CARD),
            entry("stop_payment_order", STOP_PAYMENT_ORDER),
            entry("transaction_not_allowed", TRANSACTION_NOT_PERMITTED),
            entry("try_again_later", TRY_AGAIN_LATER)
            );

    public static MappedAuthorisationRejectedReason toMappedAuthorisationRejectionReason(String stripeRejectionCode) {
        return STRIPE_REJECTION_CODE_MAP.getOrDefault(stripeRejectionCode, UNCATEGORISED);
    }

}
