package uk.gov.pay.connector.gatewayaccount.exception;

import jakarta.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class GatewayAccountCredentialsNotFoundException extends WebApplicationException {
    
    public GatewayAccountCredentialsNotFoundException(Long id) {
        super(notFoundResponse(format("Gateway account credentials with id [%s] not found.", id)));
    }

    public GatewayAccountCredentialsNotFoundException(String message) {
        super(notFoundResponse(message));
    }
    
    public static GatewayAccountCredentialsNotFoundException forExternalId (String externalId) {
        return new GatewayAccountCredentialsNotFoundException(format("Gateway account credentials with ID [%s] not found.", externalId));
    }
}
