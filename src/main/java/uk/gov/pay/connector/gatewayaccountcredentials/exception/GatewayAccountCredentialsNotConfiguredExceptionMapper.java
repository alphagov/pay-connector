package uk.gov.pay.connector.gatewayaccountcredentials.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class GatewayAccountCredentialsNotConfiguredExceptionMapper implements ExceptionMapper<GatewayAccountCredentialsNotConfiguredException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAccountCredentialsNotConfiguredExceptionMapper.class);
    
    @Override
    public Response toResponse(GatewayAccountCredentialsNotConfiguredException exception) {
        LOGGER.info(exception.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.ACCOUNT_NOT_LINKED_WITH_PSP, exception.getMessage());
        return Response.status(400)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
