package uk.gov.pay.connector.charge.exception.motoapi;

import static java.lang.String.format;

public class AuthorisationApiNotAllowedForGatewayAccountException extends RuntimeException {

    public AuthorisationApiNotAllowedForGatewayAccountException(Long gatewayAccountId) {
        super(format("Using authorisation_mode of moto_api is not allowed for this account [%d]", gatewayAccountId));
    }

}
