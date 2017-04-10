package uk.gov.pay.connector.exception;

import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class ChargeNotFoundRuntimeException extends WebApplicationException {
    public ChargeNotFoundRuntimeException(String externalId) {
        super(notFoundResponse(format("Charge with id [%s] not found.", externalId)));
    }
}
