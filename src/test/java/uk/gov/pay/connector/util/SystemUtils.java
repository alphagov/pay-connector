package uk.gov.pay.connector.util;

public class SystemUtils {
    public static String envOrThrow(String key) {
        String result = System.getenv(key);
        if (result == null) {
            throw new IllegalStateException("Cannot find value for property: " + key);
        }
        return result;
    }
}
