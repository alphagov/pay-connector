package uk.gov.pay.connector.common.exception;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;


public class UnsupportedOperationExceptionMapper implements ExceptionMapper<UnsupportedOperationException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsupportedOperationExceptionMapper.class);

    @Override
    public Response toResponse(UnsupportedOperationException exception) {
        LOGGER.error(exception.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ImmutableMap.of("message", exception.getMessage()))
                .type(APPLICATION_JSON).build();
    }
}
