package uk.gov.pay.connector.gateway.model;

public enum MappedAuthorisationRejectedReason {
    
    UNCATEGORISED,
    REFER_TO_CARD_ISSUER,
    INVALID_MERCHANT,
    CAPTURE_CARD,
    DO_NOT_HONOUR,
    TRY_ANOTHER_CARD,
    PICKUP_CARD,
    INVALID_TRANSACTION,
    INVALID_AMOUNT,
    INVALID_CARD_NUMBER,
    NO_SUCH_ISSUER,
    LOST_CARD,
    STOLEN_CARD,
    CLOSED_ACCOUNT,
    INSUFFICIENT_FUNDS,
    EXPIRED_CARD,
    TRANSACTION_NOT_PERMITTED,
    SUSPECTED_FRAUD,
    EXCEEDS_WITHDRAWAL_AMOUNT_LIMIT,
    AUTHENTICATION_REQUESTED,
    INVALID_ACCOUNT_NUMBER,
    STOP_PAYMENT_ORDER,
    REVOCATION_OF_ALL_AUTHORISATION,
    REVOCATION_OF_AUTHORISATION;
    
}
