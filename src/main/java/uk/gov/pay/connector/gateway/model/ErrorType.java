package uk.gov.pay.connector.gateway.model;

public enum ErrorType {
    GENERIC_GATEWAY_ERROR,
    GATEWAY_CONNECTION_TIMEOUT_ERROR,
    CLIENT_ERROR,
    DOWNSTREAM_ERROR
}
