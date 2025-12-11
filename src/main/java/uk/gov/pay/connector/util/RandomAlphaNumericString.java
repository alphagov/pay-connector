package uk.gov.pay.connector.util;

import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.ThreadLocalRandom.current;

public final class RandomAlphaNumericString {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String NUMS = "0123456789";
    private static final String ALPHA_NUM = CHARS + NUMS;

    private RandomAlphaNumericString() {
        // utility class - prevent instantiation
    }

    public static String randomAlphabetic(int length) {
        return randomFromCharset(CHARS, length);
    }


    public static String randomAlphaNumeric(int length) {
        return randomFromCharset(ALPHA_NUM, length);
    }

    private static String randomFromCharset(String charset, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        ThreadLocalRandom rnd = current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(rnd.nextInt(charset.length())));
        }
        return sb.toString();
    }
}
