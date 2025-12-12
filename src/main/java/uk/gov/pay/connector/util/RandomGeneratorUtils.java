package uk.gov.pay.connector.util;

import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.ThreadLocalRandom.current;

public final class RandomGeneratorUtils {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String NUMS = "0123456789";
    private static final String ALPHA_NUM = CHARS + NUMS;

    private RandomGeneratorUtils() {
        // utility class - prevent instantiation
    }


    public static long randomLong() {
        return randomLong(0, Long.MAX_VALUE);
    }

    public static long randomLong(long minInclusive, long maxExclusive) {
        return current().nextLong(minInclusive, maxExclusive);
    }

    public static int randomInt() {
        return current().nextInt(0, Integer.MAX_VALUE);
    }

    public static int randomInt(int minInclusive, int maxExclusive) {
        return current().nextInt(minInclusive, maxExclusive);
    }

    public static String randomAlphabetic(int length) {
        return randomFromCharset(CHARS, length);
    }

    public static String randomAlphanumeric(int length) {
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
