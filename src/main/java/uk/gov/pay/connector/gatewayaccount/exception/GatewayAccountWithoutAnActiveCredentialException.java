package uk.gov.pay.connector.gatewayaccount.exception;

import jakarta.ws.rs.BadRequestException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class GatewayAccountWithoutAnActiveCredentialException extends BadRequestException {
    public GatewayAccountWithoutAnActiveCredentialException(Long id) {
        super(badRequestResponse(format("Gateway account with id [%s] does not have an active credential", id)));
    }
}
