package uk.gov.pay.connector.gateway.model;

public enum MappedAuthorisationRejectedReason {

    AUTHENTICATION_REQUESTED,
    AUTHENTICATION_REQUIRED,
    CAPTURE_CARD,
    CLOSED_ACCOUNT,
    DO_NOT_HONOUR,
    DO_NOT_RETRY,
    EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT,
    EXPIRED_CARD,
    GENERIC_DECLINE,
    INCORRECT_NUMBER,
    INSUFFICIENT_FUNDS,
    INVALID_ACCOUNT_NUMBER,
    INVALID_AMOUNT,
    INVALID_CARD_NUMBER,
    INVALID_MERCHANT,
    INVALID_TRANSACTION,
    LOST_CARD,
    NO_SUCH_ISSUER,
    PICKUP_CARD,
    REENTER_TRANSACTION,
    REFER_TO_CARD_ISSUER,
    REVOCATION_OF_ALL_AUTHORISATION,
    REVOCATION_OF_AUTHORISATION,
    STOLEN_CARD,
    STOP_PAYMENT_ORDER,
    SUSPECTED_FRAUD,
    TRANSACTION_NOT_PERMITTED,
    TRY_AGAIN_LATER,
    TRY_ANOTHER_CARD,
    UNCATEGORISED
}