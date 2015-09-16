package uk.gov.pay.connector.utils;

public class EnvironmentUtils {

    public static final String WORLDPAY_USER = "GDS_CONNECTOR_WORLDPAY_USER";
    public static final String WORLDPAY_PASSWORD = "GDS_CONNECTOR_WORLDPAY_PASSWORD";

    public static String getWorldpayUser() {
        return getEnvironmentVariable(WORLDPAY_USER);
    }

    public static String getWorldpayPassword() {
        return getEnvironmentVariable(WORLDPAY_PASSWORD);
    }

    private static String getEnvironmentVariable(String variableName) {
        return System.getenv(variableName);
    }
}
