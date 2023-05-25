package uk.gov.pay.connector.gateway.model;

public enum MappedAuthorisationRejectedReason {

    AUTHENTICATION_REQUESTED(true),
    AUTHENTICATION_REQUIRED(true),
    CAPTURE_CARD(false),
    CLOSED_ACCOUNT(false),
    DO_NOT_HONOUR(true),
    DO_NOT_RETRY(false),
    EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT(true),
    EXPIRED_CARD(true),
    GENERIC_DECLINE(true),
    INCORRECT_NUMBER(false),
    INSUFFICIENT_FUNDS(true),
    INVALID_ACCOUNT_NUMBER(true),
    INVALID_AMOUNT(true),
    INVALID_CARD_NUMBER(false),
    INVALID_MERCHANT(true),
    INVALID_TRANSACTION(false),
    INVALID_CVV2(true),
    ISSUER_TEMPORARILY_UNAVAILABLE(true),
    LOST_CARD(false),
    NO_SUCH_ISSUER(true),
    PICKUP_CARD(false),
    REENTER_TRANSACTION(true),
    REFER_TO_CARD_ISSUER(true),
    REVOCATION_OF_ALL_AUTHORISATION(false),
    REVOCATION_OF_AUTHORISATION(false),
    STOLEN_CARD(true),
    STOP_PAYMENT_ORDER(false),
    SUSPECTED_FRAUD(true),
    TRANSACTION_NOT_PERMITTED(false),
    TRY_AGAIN_LATER(true),
    TRY_ANOTHER_CARD(true),
    UNCATEGORISED(true);

    private final boolean canRetry;

    MappedAuthorisationRejectedReason(boolean canRetry) {
        this.canRetry = canRetry;
    }

    public boolean canRetry() {
        return canRetry;
    }

}
