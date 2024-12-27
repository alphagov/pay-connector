package uk.gov.pay.connector.gatewayaccountcredentials.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class NoCredentialsExistForProviderExceptionMapper implements ExceptionMapper<NoCredentialsExistForProviderException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoCredentialsExistForProviderExceptionMapper.class);
    
    @Override
    public Response toResponse(NoCredentialsExistForProviderException exception) {
        LOGGER.info(exception.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, exception.getMessage());
        return Response.status(400)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
