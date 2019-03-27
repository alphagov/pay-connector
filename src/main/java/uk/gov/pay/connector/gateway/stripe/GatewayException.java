package uk.gov.pay.connector.gateway.stripe;

/**
 * This class represents any other exception (non 4xx or 5xx) that occurred while making a request to the external 
 * payment provider
 */
public class GatewayException extends Exception {
    private final transient String url;

    public GatewayException(String url, final Throwable throwable) {
        super(throwable);
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
