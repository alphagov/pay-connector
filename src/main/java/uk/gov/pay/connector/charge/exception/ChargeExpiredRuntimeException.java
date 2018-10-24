package uk.gov.pay.connector.charge.exception;

import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class ChargeExpiredRuntimeException extends WebApplicationException {
    public ChargeExpiredRuntimeException(String operationType, String chargeId) {
        super(badRequestResponse(format("%s for charge failed as already expired, %s", operationType, chargeId)));
    }
}
