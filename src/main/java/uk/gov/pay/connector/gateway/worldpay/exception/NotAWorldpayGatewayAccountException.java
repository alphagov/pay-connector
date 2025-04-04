package uk.gov.pay.connector.gateway.worldpay.exception;

import jakarta.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class NotAWorldpayGatewayAccountException extends WebApplicationException {

    private static final String ERROR_MESSAGE = "Gateway account with id %s is not a Worldpay account.";

    public NotAWorldpayGatewayAccountException(Long gatewayAccountId) {
        super(notFoundResponse(format(ERROR_MESSAGE, gatewayAccountId)));
    }
}
