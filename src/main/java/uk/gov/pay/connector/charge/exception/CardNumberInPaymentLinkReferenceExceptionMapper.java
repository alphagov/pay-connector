package uk.gov.pay.connector.charge.exception;

import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED;

public class CardNumberInPaymentLinkReferenceExceptionMapper implements ExceptionMapper<CardNumberInPaymentLinkReferenceException> {
    
    @Override
    public Response toResponse(CardNumberInPaymentLinkReferenceException exception) {
        ErrorResponse errorResponse = new ErrorResponse(CARD_NUMBER_IN_PAYMENT_LINK_REFERENCE_REJECTED, exception.getMessage());

        return Response.status(400)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
