package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.UNEXPECTED_ATTRIBUTE;

public class UnexpectedAttributeExceptionMapper implements ExceptionMapper<UnexpectedAttributeException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UnexpectedAttributeExceptionMapper.class);

    @Override
    public Response toResponse(UnexpectedAttributeException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(UNEXPECTED_ATTRIBUTE, List.of(exception.getMessage()));

        return Response.status(422)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
