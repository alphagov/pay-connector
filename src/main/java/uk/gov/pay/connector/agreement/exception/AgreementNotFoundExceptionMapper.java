package uk.gov.pay.connector.agreement.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class AgreementNotFoundExceptionMapper implements ExceptionMapper<AgreementNotFoundException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgreementNotFoundExceptionMapper.class);

    @Override
    public Response toResponse(AgreementNotFoundException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.AGREEMENT_NOT_FOUND, exception.getMessage());

        return Response.status(Response.Status.NOT_FOUND)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }

}

