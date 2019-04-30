package uk.gov.pay.connector.util;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.common.model.api.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;

import javax.ws.rs.core.Response;
import java.util.List;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.status;

public class ResponseUtil {
    protected static final Logger logger = LoggerFactory.getLogger(ResponseUtil.class);
    private static final Joiner COMMA_JOINER = Joiner.on(", ");

    public static Response fieldsMissingResponse(List<String> missingFields) {
        String message = format("Field(s) missing: [%s]", COMMA_JOINER.join(missingFields));
        return badRequestResponse(message);
    }

    public static Response fieldsInvalidSizeResponse(List<String> invalidSizeFields) {
        String message = format("Field(s) are too big: [%s]", COMMA_JOINER.join(invalidSizeFields));
        return badRequestResponse(message);
    }

    public static Response fieldsInvalidResponse(List<String> invalidFields) {
        String message = format("Field(s) are invalid: [%s]", COMMA_JOINER.join(invalidFields));
        return badRequestResponse(message);
    }

    public static Response responseWithChargeNotFound(String chargeId) {
        String message = format("Charge with id [%s] not found.", chargeId);
        return notFoundResponse(message);
    }

    public static Response responseWithRefundNotFound(String refundId) {
        String message = format("Refund with id [%s] not found.", refundId);
        return notFoundResponse(message);
    }

    public static Response badRequestResponse(String message) {
        return responseWithMessageMap(BAD_REQUEST, message);
    }

    public static Response badRequestResponse(String code, String message) {
        logger.error(message);
        return responseWith(BAD_REQUEST, message, code);
    }

    public static Response badRequestResponse(List message) {
        logger.error(message.toString());
        return responseWithMessageMap(BAD_REQUEST, message);
    }

    public static Response preconditionFailedResponse(String message) {
        logger.info(message);
        return responseWithMessageMap(PRECONDITION_FAILED, message);
    }

    public static Response notFoundResponse(String message) {
        logger.error(message);
        return responseWithMessageMap(NOT_FOUND, message);
    }

    public static Response acceptedResponse(String message) {
        return responseWithMessageMap(ACCEPTED, message);
    }

    public static Response serviceErrorResponse(String message) {
        logger.error(message);
        return responseWithMessageMap(INTERNAL_SERVER_ERROR, message);
    }

    public static Response conflictErrorResponse(String message) {
        logger.error(message);
        return responseWithMessageMap(CONFLICT, message);
    }

    public static Response forbiddenErrorResponse() {
        return status(Status.FORBIDDEN).build();
    }

    private static Response responseWithMessageMap(Status status, String message) {
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, List.of(message));
        return responseWithEntity(status, errorResponse);
    }

    private static Response responseWith(Status status, String message, String reason) {
        ErrorResponse errorResponse = new ErrorResponse(ErrorIdentifier.GENERIC, List.of(message), reason);
        return responseWithEntity(status, errorResponse);
    }

    private static Response responseWithMessageMap(Status status, Object entity) {
        return responseWithEntity(status, ImmutableMap.of("message", entity));
    }

    public static Response noContentResponse() {
        return noContent().build();
    }

    public static Response successResponseWithEntity(Object entity) {
        return responseWithEntity(OK, entity);
    }

    private static Response responseWithEntity(Status status, Object entity) {
        return status(status).entity(entity).build();
    }
}
