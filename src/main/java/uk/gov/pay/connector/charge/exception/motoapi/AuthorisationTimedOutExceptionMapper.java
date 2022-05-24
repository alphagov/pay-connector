package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.AUTHORISATION_TIMEOUT;

public class AuthorisationTimedOutExceptionMapper implements ExceptionMapper<AuthorisationTimedOutException> {
    @Override
    public Response toResponse(AuthorisationTimedOutException exception) {
        ErrorResponse errorResponse = new ErrorResponse(AUTHORISATION_TIMEOUT, exception.getMessage());

        return Response.status(500)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
