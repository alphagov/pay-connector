package uk.gov.pay.connector.util;

import com.google.common.collect.ImmutableMap;

import javax.ws.rs.core.Response;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

public class ResponseUtil {

    public static Response badResponse(String message) {
        return responseWithMessage(BAD_REQUEST, message);
    }

    public static Response notFoundResponse(String message) {
        return responseWithMessage(NOT_FOUND, message);
    }

    private static Response responseWithMessage(Response.Status status, String message) {
        return Response.status(status).entity(ImmutableMap.of("message", message)).build();
    }

}
