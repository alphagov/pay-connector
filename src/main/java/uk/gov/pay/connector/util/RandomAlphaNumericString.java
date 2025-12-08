package uk.gov.pay.connector.util;

import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.ThreadLocalRandom.current;

public final class RandomAlphaNumericString {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private RandomAlphaNumericString() {
        // utility class - prevent instantiation
    }

    public static String randomAlphaNumeric(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        ThreadLocalRandom rnd = current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(rnd.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
