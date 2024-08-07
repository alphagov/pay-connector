package uk.gov.pay.connector.gatewayaccount.exception;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

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
    
    public GatewayAccountNotFoundException(String serviceId, GatewayAccountType accountType) {
        this(format("Gateway account not found for service ID [%s] and account type [%s]", serviceId, accountType));
    }
    
    public static GatewayAccountNotFoundException forNonWorldpayAccount(String serviceId, GatewayAccountType accountType) {
        return new GatewayAccountNotFoundException(format("Gateway account for service ID [%s] and account type [%s] is not a Worldpay account.", serviceId, accountType));
    }
}
