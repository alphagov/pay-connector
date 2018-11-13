package uk.gov.pay.connector.gateway.stripe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.UUID;

import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

public class StripeExceptionMapper implements ExceptionMapper<StripeException> {

    private static final Logger logger = LoggerFactory.getLogger(StripeExceptionMapper.class);

    @Override
    public Response toResponse(StripeException exception) {
        String errorId = UUID.randomUUID().toString();
        logger.error("There was error calling {}. Response code from Stripe: {}, Reason: {}, ErrorId: {}", 
                exception.getUri(), exception.getStatusCode(), exception.getMessage(), errorId);
        String exceptionType = exception.getType();
        switch (exceptionType) {
            case "card_error":
                return badRequestResponse(exception.getMessage());
            default:
                return serviceErrorResponse("There was an internal server error. ErrorId: " + errorId);
        }
    }
}
