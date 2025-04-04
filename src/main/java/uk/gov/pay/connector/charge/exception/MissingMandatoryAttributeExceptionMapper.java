package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.MISSING_MANDATORY_ATTRIBUTE;

public class MissingMandatoryAttributeExceptionMapper implements ExceptionMapper<MissingMandatoryAttributeException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MissingMandatoryAttributeExceptionMapper.class);

    @Override
    public Response toResponse(MissingMandatoryAttributeException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(MISSING_MANDATORY_ATTRIBUTE, List.of(exception.getMessage()));

        return Response.status(422)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
