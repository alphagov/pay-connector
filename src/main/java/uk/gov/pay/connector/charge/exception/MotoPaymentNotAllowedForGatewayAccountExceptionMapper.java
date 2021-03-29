package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class MotoPaymentNotAllowedForGatewayAccountExceptionMapper implements ExceptionMapper<MotoPaymentNotAllowedForGatewayAccountException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MotoPaymentNotAllowedForGatewayAccountExceptionMapper.class);

    private static final String RESPONSE_ERROR_MESSAGE = "MOTO payments are not enabled for this gateway account";

    @Override
    public Response toResponse(MotoPaymentNotAllowedForGatewayAccountException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.MOTO_NOT_ALLOWED, List.of(RESPONSE_ERROR_MESSAGE));

        return Response.status(422)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
