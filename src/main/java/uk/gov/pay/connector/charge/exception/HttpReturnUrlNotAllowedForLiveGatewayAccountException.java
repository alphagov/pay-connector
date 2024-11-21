package uk.gov.pay.connector.charge.exception;

public final class HttpReturnUrlNotAllowedForLiveGatewayAccountException implements ErrorListMapper.Error {
    private final String message;

    public HttpReturnUrlNotAllowedForLiveGatewayAccountException(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
