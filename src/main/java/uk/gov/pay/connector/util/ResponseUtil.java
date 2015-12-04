package uk.gov.pay.connector.util;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
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
        return responseWithMessage(BAD_REQUEST, message);
    }

    public static Response notFoundResponse(Logger logger, String message) {
        logger.error(message);
        return responseWithMessage(NOT_FOUND, message);
    }

    public static Response serviceErrorResponse(Logger logger, String message) {
        logger.error(message);
        return responseWithMessage(INTERNAL_SERVER_ERROR, message);
    }

    public static Response noContentResponse() {
        return Response.noContent().build();
    }

    private static Response responseWithMessage(Response.Status status, String message) {
        return Response.status(status).entity(ImmutableMap.of("message", message)).build();
    }

    public static Response entityCreatedResponse(URI selfUri, Map<String, Object> responseData) {
        return Response.created(selfUri).entity(responseData).build();
    }

    public static Response entityResponse(Map<String, Object> responseData) {
        return Response.ok(responseData).build();
    }
}
