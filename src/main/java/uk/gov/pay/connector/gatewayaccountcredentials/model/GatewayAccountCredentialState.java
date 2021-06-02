package uk.gov.pay.connector.gatewayaccountcredentials.model;

public enum GatewayAccountCredentialState {
    CREATED,
    ENTERED,
    VERIFIED_WITH_LIVE_PAYMENT,
    ACTIVE,
    RETIRED;
}
