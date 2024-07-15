package uk.gov.pay.connector.gatewayaccount.exception;

import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.conflictErrorResponse;

public class MultipleLiveGatewayAccountsException extends WebApplicationException {

    private MultipleLiveGatewayAccountsException(String message) {
        super(conflictErrorResponse(message));
    }
    
    public static MultipleLiveGatewayAccountsException multipleLiveGatewayAccounts(String serviceId) {
        return new MultipleLiveGatewayAccountsException(format("Multiple live gateway accounts found for service [%s]", serviceId));
    }
    
    public static MultipleLiveGatewayAccountsException liveGatewayAccountAlreadyExists(String serviceId) {
        return new MultipleLiveGatewayAccountsException(format("There is already a live gateway account for service [%s]", serviceId));
    }
}
