package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class ChargeExceptionMapper implements ExceptionMapper<ChargeException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChargeExceptionMapper.class);
    
    @Override
    public Response toResponse(ChargeException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(exception.getErrorIdentifier(), exception.getMessage());

        return Response.status(exception.getHttpResponseStatusCode())
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
