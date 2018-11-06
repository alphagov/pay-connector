package uk.gov.pay.connector.gateway.exception;

import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.GatewayError;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class GatewayErrorExceptionMapper implements ExceptionMapper<GatewayError> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayErrorExceptionMapper.class);

    @Override
    public Response toResponse(GatewayError exception) {
        LOGGER.error(exception.toString());
        return Response.status(500).type(APPLICATION_JSON).build();
    }
}
