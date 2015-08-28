package uk.gov.pay.connector.util;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;

import javax.ws.rs.core.Response;
import java.util.List;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

public class ResponseUtil {
    public static final Joiner COMMA_JOINER = Joiner.on(", ");

    public static Response badResponse(String message) {
        return responseWithMessage(BAD_REQUEST, message);
    }

    public static Response notFoundResponse(String message) {
        return responseWithMessage(NOT_FOUND, message);
    }

    public static Response fieldsMissingResponse(Logger logger, List<String> missingFields) {
        String message = String.format("Field(s) missing: [%s]", COMMA_JOINER.join(missingFields));
        logger.error(message);
        return responseWithMessage(BAD_REQUEST, message);
    }

    private static Response responseWithMessage(Response.Status status, String message) {
        return Response.status(status).entity(ImmutableMap.of("message", message)).build();
    }

}
