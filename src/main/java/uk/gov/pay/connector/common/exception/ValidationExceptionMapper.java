package uk.gov.pay.connector.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class ValidationExceptionMapper implements ExceptionMapper<ValidationException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidationExceptionMapper.class);

    @Override
    public Response toResponse(ValidationException exception) {
        LOGGER.error(String.join("\n", exception.getErrors()));
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, exception.getErrors());
        return Response.status(400).entity(errorResponse).type(APPLICATION_JSON).build();
    }
}
