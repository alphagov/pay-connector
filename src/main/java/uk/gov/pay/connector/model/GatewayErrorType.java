package uk.gov.pay.connector.model;

public enum GatewayErrorType {
    ChargeNotFound,
    GenericGatewayError,
    UnexpectedStatusCodeFromGateway,
    MalformedResponseReceivedFromGateway,
    UnknownHostException,
    ;
}
