package uk.gov.pay.connector.util;

import org.glassfish.jersey.internal.util.Base64;

public class AuthUtil {
    public static String encode(String username, String password) {
        return "Basic " + Base64.encodeAsString(username + ":" + password);
    }
}
