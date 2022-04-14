package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.ONE_TIME_TOKEN_ALREADY_USED;

public class OneTimeTokenAlreadyUsedExceptionMapper implements ExceptionMapper<OneTimeTokenAlreadyUsedException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OneTimeTokenAlreadyUsedExceptionMapper.class);

    @Override
    public Response toResponse(OneTimeTokenAlreadyUsedException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ONE_TIME_TOKEN_ALREADY_USED, exception.getMessage());

        return Response.status(BAD_REQUEST)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
