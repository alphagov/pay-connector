package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class AgreementIdWithIncompatibleOtherOptionsExceptionMapper
        implements ExceptionMapper<AgreementIdWithIncompatibleOtherOptionsException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgreementIdWithIncompatibleOtherOptionsExceptionMapper.class);
    
    @Override
    public Response toResponse(AgreementIdWithIncompatibleOtherOptionsException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, exception.getMessage());

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
