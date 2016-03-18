package uk.gov.pay.connector.util;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;

import javax.ws.rs.core.Response;
import java.util.List;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.*;
import static javax.ws.rs.core.Response.Status.*;

public class ResponseUtil {
    public static final Joiner COMMA_JOINER = Joiner.on(", ");

    public static Response fieldsMissingResponse(Logger logger, List<String> missingFields) {
        String message = format("Field(s) missing: [%s]", COMMA_JOINER.join(missingFields));
        return badRequestResponse(logger, message);
    }

    public static Response fieldsInvalidSizeResponse(Logger logger, List<String> invalidSizeFields) {
        String message = format("Field(s) are too big: [%s]", COMMA_JOINER.join(invalidSizeFields));
        return badRequestResponse(logger, message);
    }

    public static Response responseWithChargeNotFound(Logger logger, String chargeId) {
        String message = format("Charge with id [%s] not found.", chargeId);
        return notFoundResponse(logger, message);
    }

    public static Response badRequestResponse(Logger logger, String message) {
        logger.error(message);
        return responseWithMessageMap(BAD_REQUEST, message);
    }

    public static Response notFoundResponse(Logger logger, String message) {
        logger.error(message);
        return responseWithMessageMap(NOT_FOUND, message);
    }

    public static Response acceptedResponse(Logger logger, String message) {
        logger.error(message);
        return responseWithMessageMap(ACCEPTED, message);
    }

    public static Response serviceErrorResponse(Logger logger, String message) {
        logger.error(message);
        return responseWithMessageMap(INTERNAL_SERVER_ERROR, message);
    }

    public static Response conflictErrorResponse(Logger logger, String message) {
        logger.error(message);
        return responseWithMessageMap(CONFLICT, message);
    }

    private static Response responseWithMessageMap(Status status, String message) {
        return responseWithEntity(status, ImmutableMap.of("message", message));
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
