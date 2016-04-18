package uk.gov.pay.connector.exception;

import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

public class GenericGatewayRuntimeException extends ConnectorRuntimeException {
    public GenericGatewayRuntimeException(String message) {
        super(serviceErrorResponse(message));
    }
}
