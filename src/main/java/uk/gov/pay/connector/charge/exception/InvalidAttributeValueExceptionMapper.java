package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.INVALID_ATTRIBUTE_VALUE;

public class InvalidAttributeValueExceptionMapper implements ExceptionMapper<InvalidAttributeValueException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvalidAttributeValueExceptionMapper.class);

    @Override
    public Response toResponse(InvalidAttributeValueException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(INVALID_ATTRIBUTE_VALUE, List.of(exception.getMessage()));

        return Response.status(422)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
