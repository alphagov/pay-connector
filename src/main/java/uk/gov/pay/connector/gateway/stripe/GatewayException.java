package uk.gov.pay.connector.gateway.stripe;

public class GatewayException extends Exception {
    private final transient String url;

    GatewayException(String url, final Throwable throwable) {
        super(throwable);
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
