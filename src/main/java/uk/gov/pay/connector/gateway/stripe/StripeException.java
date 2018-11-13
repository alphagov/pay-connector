package uk.gov.pay.connector.gateway.stripe;

import uk.gov.pay.connector.gateway.stripe.json.StripeError;

import java.net.URI;

public class StripeException extends RuntimeException {
    private StripeError stripeError;
    private URI url;
    private int status;

    public StripeException(StripeError stripeError, URI url, int status) {
        this.stripeError = stripeError;
        this.url = url;
        this.status = status;
    }

    public URI getUri() {
        return url;
    }

    public String getMessage() {
        return stripeError.getError().getMessage();
    }
    
    public String getType() {
        return stripeError.getError().getType();
    }

    public int getStatusCode() {
        return status;
    }
}
