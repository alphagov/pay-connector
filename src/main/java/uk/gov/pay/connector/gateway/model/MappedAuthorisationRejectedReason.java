package uk.gov.pay.connector.gateway.model;

public enum MappedAuthorisationRejectedReason {

    AUTHENTICATION_REQUESTED(true),
    DO_NOT_HONOUR(true),
    EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT(true),
    GENERIC_DECLINE(true),
    INSUFFICIENT_FUNDS(true),
    INVALID_ACCOUNT_NUMBER(true),
    INVALID_AMOUNT(true),
    INVALID_MERCHANT(true),
    ISSUER_TEMPORARILY_UNAVAILABLE(true),
    REENTER_TRANSACTION(true),
    REFER_TO_CARD_ISSUER(true),
    SUSPECTED_FRAUD(true),
    TRY_AGAIN_LATER(true),
    TRY_ANOTHER_CARD(true),
    UNCATEGORISED(true),

    AUTHENTICATION_REQUIRED(false),
    CAPTURE_CARD(false),
    CLOSED_ACCOUNT(false),
    DO_NOT_RETRY(false),
    EXPIRED_CARD(false),
    INCORRECT_NUMBER(false),
    INVALID_CARD_NUMBER(false),
    INVALID_TRANSACTION(false),
    INVALID_CVV2(false),
    LOST_CARD(false),
    NO_SUCH_ISSUER(false),
    PICKUP_CARD(false),
    REVOCATION_OF_ALL_AUTHORISATION(false),
    REVOCATION_OF_AUTHORISATION(false),
    STOLEN_CARD(false),
    STOP_PAYMENT_ORDER(false),
    TRANSACTION_NOT_PERMITTED(false);

    private final boolean recurringPaymentCanBeRetried;

    MappedAuthorisationRejectedReason(boolean recurringPaymentCanBeRetried) {
        this.recurringPaymentCanBeRetried = recurringPaymentCanBeRetried;
    }

    public boolean canRetry() {
        return recurringPaymentCanBeRetried;
    }

}
