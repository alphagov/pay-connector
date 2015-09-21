package uk.gov.pay.connector.model;

import static uk.gov.pay.connector.model.GatewayErrorType.BaseGatewayError;

public class GatewayError {
    private String message;
    private GatewayErrorType errorType;

    public GatewayError(String message, GatewayErrorType errorType) {
        this.message = message;
        this.errorType = errorType;
    }

    public static GatewayError baseGatewayError(String msg) {
        return new GatewayError(msg, BaseGatewayError);
    }

    public String getMessage() {
        return message;
    }

    public GatewayErrorType getErrorType() {
        return errorType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GatewayError that = (GatewayError) o;

        if (errorType != that.errorType) return false;
        if (message != null ? !message.equals(that.message) : that.message != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = message != null ? message.hashCode() : 0;
        result = 31 * result + (errorType != null ? errorType.hashCode() : 0);
        return result;
    }
}
