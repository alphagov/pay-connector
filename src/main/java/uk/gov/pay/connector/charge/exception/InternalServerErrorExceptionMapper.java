package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.commons.model.ErrorIdentifier.GENERIC;

public class InternalServerErrorExceptionMapper implements ExceptionMapper<InternalServerErrorException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalServerErrorExceptionMapper.class);
    
    @Override
    public Response toResponse(InternalServerErrorException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(GENERIC, exception.getMessage());

        return Response.status(500)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
