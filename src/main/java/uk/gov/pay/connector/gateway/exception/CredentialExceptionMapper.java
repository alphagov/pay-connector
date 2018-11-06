package uk.gov.pay.connector.gateway.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.exception.CredentialsException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class CredentialExceptionMapper implements ExceptionMapper<CredentialsException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialExceptionMapper.class);

    @Override
    public Response toResponse(CredentialsException exception) {
        LOGGER.error(exception.toString());
        return Response.status(500).type(APPLICATION_JSON).build();
    }
}
