package uk.gov.pay.connector.charge.exception;

import jakarta.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class ChargeEventNotFoundRuntimeException extends WebApplicationException {
    public ChargeEventNotFoundRuntimeException(String externalId) {
        super(notFoundResponse(format("Charge with id [%s] does not have any events.", externalId)));
    }
}
