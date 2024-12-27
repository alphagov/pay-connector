package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class IncorrectAuthorisationModeForSavePaymentToAgreementExceptionMapper
        implements ExceptionMapper<IncorrectAuthorisationModeForSavePaymentToAgreementException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(IncorrectAuthorisationModeForSavePaymentToAgreementExceptionMapper.class);
    
    @Override
    public Response toResponse(IncorrectAuthorisationModeForSavePaymentToAgreementException exception) {
        LOGGER.info(exception.getMessage());

        var errorResponse = new ErrorResponse(ErrorIdentifier.INCORRECT_AUTHORISATION_MODE_FOR_SAVE_PAYMENT_INSTRUMENT_TO_AGREEMENT, exception.getMessage());

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
