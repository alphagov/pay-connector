package uk.gov.pay.connector.exception;

import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class RefundNotAvailableRuntimeException extends WebApplicationException {
    public RefundNotAvailableRuntimeException(String chargeId) {
        super(badRequestResponse(format("Charge with id [%s] not available for refund.", chargeId)));
    }
}
