package uk.gov.pay.connector.gatewayaccount.exception;

import jakarta.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.conflictErrorResponse;

public class MultipleStripeTestGatewayAccountsException extends WebApplicationException {
    
    public MultipleStripeTestGatewayAccountsException(String serviceId, 
                                                      String gatewayAccountExternalId, 
                                                      String gatewayAccountCredentialExternalId) {
        super(conflictErrorResponse(format("Service '%s' already has an active Stripe gateway account with external id " +
                "'%s' with gateway account credential is '%s' ", serviceId, gatewayAccountExternalId, gatewayAccountCredentialExternalId)));
    }
}
