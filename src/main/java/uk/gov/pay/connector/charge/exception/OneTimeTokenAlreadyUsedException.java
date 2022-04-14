package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.BadRequestException;

public class OneTimeTokenAlreadyUsedException extends BadRequestException {
    public OneTimeTokenAlreadyUsedException() {
        super("The one_time_token has already been used");
    }
}
