package uk.gov.pay.connector.gateway.worldpay.exception;

import org.apache.http.HttpStatus;

import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;

public class NotAWorldpayGatewayAccountException extends WebApplicationException {

    private static final String ERROR_MESSAGE = "Gateway account with id %s is not a Worldpay account.";

    public NotAWorldpayGatewayAccountException(Long gatewayAccountId) {
        super(format(ERROR_MESSAGE, gatewayAccountId), HttpStatus.SC_NOT_FOUND);
    }
}
