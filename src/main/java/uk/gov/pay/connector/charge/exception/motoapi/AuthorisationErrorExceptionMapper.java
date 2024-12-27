package uk.gov.pay.connector.charge.exception.motoapi;

import uk.gov.pay.connector.common.model.api.ErrorResponse;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.AUTHORISATION_ERROR;

public class AuthorisationErrorExceptionMapper implements ExceptionMapper<AuthorisationErrorException> {
    
    @Override
    public Response toResponse(AuthorisationErrorException exception) {
        ErrorResponse errorResponse = new ErrorResponse(AUTHORISATION_ERROR, exception.getMessage());

        return Response.status(500)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
