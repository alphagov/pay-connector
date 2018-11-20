package uk.gov.pay.connector.gateway.stripe;

/**
 * This class represents 5xx exceptions
 */
public class DownstreamException extends Exception {
    private final int statusCode;
    private final String jsonErrorEntity;

    public DownstreamException(int statusCode, String jsonErrorEntity) {
        this.statusCode = statusCode;
        this.jsonErrorEntity = jsonErrorEntity;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String getMessage() {
        return jsonErrorEntity;
    }
}
