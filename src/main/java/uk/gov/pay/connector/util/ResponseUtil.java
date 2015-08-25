package uk.gov.pay.connector.util;

import com.google.common.collect.ImmutableMap;

import javax.ws.rs.core.Response;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

public class ResponseUtil {

    public static Response badResponse(String message) {
        return Response.status(BAD_REQUEST).entity(ImmutableMap.of("message", message)).build();
    }


}
