package uk.gov.pay.connector.token.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.exception.AgreementNotFoundException;
import uk.gov.pay.connector.agreement.exception.AgreementNotFoundExceptionMapper;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class TokenNotFoundExceptionMapper implements ExceptionMapper<TokenNotFoundException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenNotFoundExceptionMapper.class);

    @Override
    public Response toResponse(TokenNotFoundException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, exception.getMessage());

        return Response.status(Response.Status.NOT_FOUND)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }

}
