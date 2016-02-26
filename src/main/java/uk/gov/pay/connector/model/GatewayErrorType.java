package uk.gov.pay.connector.model;

public enum GatewayErrorType {
    ChargeNotFound,

    IllegalStateError,
    GenericGatewayError,
    UnexpectedStatusCodeFromGateway,
    MalformedResponseReceivedFromGateway,
    GatewayUrlDnsError,
    GatewayConnectionTimeoutError,
    GatewayConnectionSocketError,

    OperationAlreadyInProgress
}
