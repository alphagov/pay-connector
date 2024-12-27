package uk.gov.pay.connector.agreement.exception;

import jakarta.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import jakarta.ws.rs.core.Response;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

public class RecurringCardPaymentsNotAllowedExceptionMapper 
        implements ExceptionMapper<RecurringCardPaymentsNotAllowedException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecurringCardPaymentsNotAllowedExceptionMapper.class);

    private static final String RESPONSE_ERROR_MESSAGE = "Recurring payment agreements are not enabled on this account";

    @Override
    public Response toResponse(RecurringCardPaymentsNotAllowedException exception) {
        LOGGER.info(exception.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.RECURRING_CARD_PAYMENTS_NOT_ALLOWED, List.of(RESPONSE_ERROR_MESSAGE));

        return Response.status(422)
                .entity(errorResponse)
                .type(APPLICATION_JSON)
                .build();
    }
}

