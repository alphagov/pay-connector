package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.GENERIC;

public class ConflictWebApplicationExceptionMapper implements ExceptionMapper<ConflictWebApplicationException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConflictWebApplicationExceptionMapper.class);

    @Override
    public Response toResponse(ConflictWebApplicationException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(GENERIC, exception.getMessage());

        return Response.status(409)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }

}
