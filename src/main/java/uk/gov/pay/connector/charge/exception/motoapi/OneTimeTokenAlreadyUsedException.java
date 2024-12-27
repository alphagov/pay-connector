package uk.gov.pay.connector.charge.exception.motoapi;

import jakarta.ws.rs.BadRequestException;

public class OneTimeTokenAlreadyUsedException extends BadRequestException {
    public OneTimeTokenAlreadyUsedException() {
        super("The one_time_token has already been used");
    }
}
