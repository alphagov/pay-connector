package uk.gov.pay.connector.charge.exception.motoapi;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.CARD_NUMBER_REJECTED;

public class CardNumberRejectedExceptionMapper implements ExceptionMapper<CardNumberRejectedException> {

    @Override
    public Response toResponse(CardNumberRejectedException exception) {
        ErrorResponse errorResponse = new ErrorResponse(CARD_NUMBER_REJECTED, exception.getMessage());

        return Response.status(402)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
