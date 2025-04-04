package uk.gov.pay.connector.charge.exception.motoapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.ONE_TIME_TOKEN_INVALID;

public class OneTimeTokenInvalidExceptionMapper implements ExceptionMapper<OneTimeTokenInvalidException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OneTimeTokenInvalidExceptionMapper.class);

    @Override
    public Response toResponse(OneTimeTokenInvalidException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ONE_TIME_TOKEN_INVALID, List.of(exception.getMessage()));

        return Response.status(BAD_REQUEST)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
