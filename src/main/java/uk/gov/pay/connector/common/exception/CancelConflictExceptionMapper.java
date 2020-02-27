package uk.gov.pay.connector.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class CancelConflictExceptionMapper implements ExceptionMapper<CancelConflictException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CancelConflictExceptionMapper.class);

    @Override
    public Response toResponse(CancelConflictException e) {
        LOGGER.info(e.getMessage());
        var errorResponse = new ErrorResponse(e.getConflictResult().getErrorIdentifier(), e.getMessage());
        return Response.status(409).entity(errorResponse).type(APPLICATION_JSON).build();
    }
}
