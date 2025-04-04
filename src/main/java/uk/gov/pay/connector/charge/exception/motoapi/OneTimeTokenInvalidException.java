package uk.gov.pay.connector.charge.exception.motoapi;

import jakarta.ws.rs.BadRequestException;

public class OneTimeTokenInvalidException extends BadRequestException {
    public OneTimeTokenInvalidException() {
        super("The one_time_token is not valid");
    }
}
