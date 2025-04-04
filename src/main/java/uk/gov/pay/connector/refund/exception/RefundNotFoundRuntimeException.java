package uk.gov.pay.connector.refund.exception;

import jakarta.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

public class RefundNotFoundRuntimeException extends WebApplicationException {
    public RefundNotFoundRuntimeException(String refundExternalId) {
        super(notFoundResponse(format("Refund with id [%s] not found.", refundExternalId)));
    }
}
