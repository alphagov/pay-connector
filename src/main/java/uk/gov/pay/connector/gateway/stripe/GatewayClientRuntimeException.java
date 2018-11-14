package uk.gov.pay.connector.gateway.stripe;

public class GatewayClientRuntimeException extends RuntimeException {
    private final transient String url;

    GatewayClientRuntimeException(String url, final Throwable throwable) {
        super(throwable);
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
