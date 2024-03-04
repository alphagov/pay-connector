package uk.gov.pay.connector.gatewayaccount.exception;

import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class GatewayAccountNotFoundException extends WebApplicationException {
    public GatewayAccountNotFoundException(Long accountId) {
        super(notFoundResponse(format("Gateway Account with id [%s] not found.", accountId)));
    }
    
    public GatewayAccountNotFoundException(String message) {
        super(notFoundResponse(message));
    }
}
