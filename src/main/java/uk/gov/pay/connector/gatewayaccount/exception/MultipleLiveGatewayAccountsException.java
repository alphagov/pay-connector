package uk.gov.pay.connector.gatewayaccount.exception;

import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.conflictErrorResponse;

public class MultipleLiveGatewayAccountsException extends WebApplicationException {
    
    public MultipleLiveGatewayAccountsException(String serviceId) {
        super(conflictErrorResponse(format("Multiple live gateway accounts found for service [%s]", serviceId)));
    }
}
