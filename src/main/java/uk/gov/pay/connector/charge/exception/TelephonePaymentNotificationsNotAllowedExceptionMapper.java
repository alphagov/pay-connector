package uk.gov.pay.connector.charge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class TelephonePaymentNotificationsNotAllowedExceptionMapper implements ExceptionMapper<TelephonePaymentNotificationsNotAllowedException> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelephonePaymentNotificationsNotAllowedExceptionMapper.class);

    private static final String RESPONSE_ERROR_MESSAGE = "Telephone payment notifications are not enabled for this gateway account";

    @Override
    public Response toResponse(TelephonePaymentNotificationsNotAllowedException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.TELEPHONE_PAYMENT_NOTIFICATIONS_NOT_ALLOWED, List.of(RESPONSE_ERROR_MESSAGE));

        return Response.status(403)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}
