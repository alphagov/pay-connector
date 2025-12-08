package uk.gov.pay.connector.charge.exception.motoapi;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class AuthorisationApiNotAllowedForGatewayAccountExceptionMapper implements ExceptionMapper<AuthorisationApiNotAllowedForGatewayAccountException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorisationApiNotAllowedForGatewayAccountExceptionMapper.class);

    @Override
    public Response toResponse(AuthorisationApiNotAllowedForGatewayAccountException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.AUTHORISATION_API_NOT_ALLOWED, exception.getMessage());

        return Response.status(422)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
