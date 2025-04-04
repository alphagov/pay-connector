package uk.gov.pay.connector.gateway.exception;

import jakarta.ws.rs.WebApplicationException;

import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

public class GenericGatewayRuntimeException extends WebApplicationException {
    public GenericGatewayRuntimeException(String message) {
        super(serviceErrorResponse(message));
    }
}
