package uk.gov.pay.connector.common.exception;

import jakarta.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class IllegalStateRuntimeException extends WebApplicationException {
    public IllegalStateRuntimeException(String chargeId) {
        super(badRequestResponse(format("Charge not in correct state to be processed, %s", chargeId)));
    }
}
