package uk.gov.pay.connector.util;

import org.apache.commons.lang3.RandomUtils;

import java.util.concurrent.ThreadLocalRandom;

import static java.util.concurrent.ThreadLocalRandom.current;

public final class RandomTestDataGeneratorUtils {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final String NUMS = "0123456789";
    private static final String ALPHA_NUM = CHARS + NUMS;

    private RandomTestDataGeneratorUtils() {
        // utility class - prevent instantiation
    }


    public static long secureRandomLong() {
        return secureRandomLong(0, Long.MAX_VALUE);
    }

    public static long secureRandomLong(long minInclusive, long maxExclusive) {
        return RandomUtils.secure().randomLong(minInclusive, maxExclusive);
    }

    public static int secureRandomInt() {
        return secureRandomInt(0, Integer.MAX_VALUE);
    }

    public static int secureRandomInt(int minInclusive, int maxExclusive) {
        return RandomUtils.secure().randomInt(minInclusive, maxExclusive);
    }

    public static String randomAlphabetic(int length) {
        return randomFromCharset(CHARS, length);
    }

    public static String randomAlphanumeric(int length) {
        return randomFromCharset(ALPHA_NUM, length);
    }

    private static String randomFromCharset(String possibleCharacters, int length) {
        ThreadLocalRandom rnd = current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(possibleCharacters.charAt(rnd.nextInt(possibleCharacters.length())));
        }
        return sb.toString();
    }
}
