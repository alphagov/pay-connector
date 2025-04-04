package uk.gov.pay.connector.charge.exception.motoapi;

import jakarta.ws.rs.BadRequestException;

public class OneTimeTokenUsageInvalidForMotoApiException extends BadRequestException {
    public OneTimeTokenUsageInvalidForMotoApiException() {
        super("The one_time_token is not a valid moto api token");
    }
}
