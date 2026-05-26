package uk.gov.pay.connector.gatewayaccount.exception;

import jakarta.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class NonStripeAccountException extends WebApplicationException {
    public NonStripeAccountException(String serviceId) {
        super(badRequestResponse(format("No Stripe test account exists for service '%s'", serviceId)));
    }
}
