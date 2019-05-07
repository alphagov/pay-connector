package uk.gov.pay.connector.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;


public class UnsupportedOperationExceptionMapper implements ExceptionMapper<UnsupportedOperationException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsupportedOperationExceptionMapper.class);

    @Override
    public Response toResponse(UnsupportedOperationException exception) {
        LOGGER.error(exception.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, List.of(exception.getMessage()));
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorResponse)
                .type(APPLICATION_JSON).build();
    }
}
