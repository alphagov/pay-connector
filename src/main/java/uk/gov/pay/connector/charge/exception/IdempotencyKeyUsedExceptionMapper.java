package uk.gov.pay.connector.charge.exception;

import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class IdempotencyKeyUsedExceptionMapper implements ExceptionMapper<IdempotencyKeyUsedException> {
    @Override
    public Response toResponse(IdempotencyKeyUsedException exception) {
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.IDEMPOTENCY_KEY_USED, exception.getMessage());

        return Response.status(Response.Status.CONFLICT)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
