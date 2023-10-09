package uk.gov.pay.connector.util;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import java.util.List;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.PAYMENT_REQUIRED;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.status;
import static uk.gov.service.payments.commons.model.ErrorIdentifier.AUTHORISATION_REJECTED;

public class ResponseUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseUtil.class);
    private static final Joiner COMMA_JOINER = Joiner.on(", ");

    public static Response fieldsMissingResponse(List<String> missingFields) {
        String message = format("Field(s) missing: [%s]", COMMA_JOINER.join(missingFields));
        return badRequestResponse(message);
    }

    public static Response fieldsInvalidSizeResponse(List<String> invalidSizeFields) {
        String message = format("Field(s) are too big: [%s]", COMMA_JOINER.join(invalidSizeFields));
        return badRequestResponse(message);
    }

    public static Response responseWithChargeNotFound(String chargeId) {
        String message = format("Charge with id [%s] not found.", chargeId);
        return notFoundResponse(message);
    }

    public static Response responseWithGatewayTransactionNotFound(String gatewayTransactionId) {
        String message = format("Charge with gateway transaction id [%s] not found.", gatewayTransactionId);
        return notFoundResponse(message);
    }

    public static Response responseWithRefundNotFound(String refundId) {
        String message = format("Refund with id [%s] not found.", refundId);
        return notFoundResponse(message);
    }

    public static Response badRequestResponse(String message) {
        return buildErrorResponse(BAD_REQUEST, message);
    }

    public static Response authorisationRejectedResponse(String message) {
        return buildErrorResponse(BAD_REQUEST, AUTHORISATION_REJECTED, message);
    }

    public static Response badRequestResponse(List<String> messages) {
        LOGGER.error(messages.toString());
        return buildErrorResponse(BAD_REQUEST, messages);
    }

    public static Response notFoundResponse(String message) {
        LOGGER.info(message);
        return buildErrorResponse(NOT_FOUND, message);
    }

    public static Response acceptedResponse(String message) {
        return buildErrorResponse(ACCEPTED, message);
    }

    public static Response serviceErrorResponse(String message) {
        LOGGER.info(message);
        return buildErrorResponse(INTERNAL_SERVER_ERROR, message);
    }

    public static Response gatewayErrorResponse(String message) {
        LOGGER.info(message);
        return buildErrorResponse(PAYMENT_REQUIRED, message);
    }
    
    public static Response conflictErrorResponse(String message) {
        LOGGER.info(message);
        return buildErrorResponse(CONFLICT, message);
    }

    public static Response forbiddenErrorResponse() {
        return status(Status.FORBIDDEN).build();
    }

    public static Response buildErrorResponse(Status status, String message) {
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, List.of(message));
        return responseWithEntity(status, errorResponse);
    }

    private static Response buildErrorResponse(Status status, List<String> messages) {
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, messages);
        return responseWithEntity(status, errorResponse);
    }

    public static Response buildErrorResponse(Status status, ErrorIdentifier errorIdentifier, String message) {
        ErrorResponse errorResponse = new ErrorResponse(errorIdentifier, message);
        return responseWithEntity(status, errorResponse);
    }
    public static Response buildErrorResponse(Status status, ErrorIdentifier errorIdentifier, List<String> messages) {
        ErrorResponse errorResponse = new ErrorResponse(errorIdentifier, messages);
        return responseWithEntity(status, errorResponse);
    }

    public static Response noContentResponse() {
        return noContent().build();
    }

    public static Response successResponseWithEntity(Object entity) {
        return responseWithEntity(OK, entity);
    }

    public static Response badRequestResponseWithEntity(Object entity) {
        return responseWithEntity(BAD_REQUEST, entity);
    }

    private static Response responseWithEntity(Status status, Object entity) {
        return status(status).entity(entity).build();
    }
}
