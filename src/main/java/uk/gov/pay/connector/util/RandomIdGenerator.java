package uk.gov.pay.connector.util;

import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

public class RandomIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * This method will generate a URL safe random string.
     * This works by choosing 130 bits from a cryptographically secure random bit generator,
     * and encoding them in base-32.
     * <p> 128 bits is considered to be cryptographically strong,
     * but each digit in a base 32 number can encode 5 bits, so 128 is rounded up to the next multiple of 5.
     * This encoding is compact and efficient, with 5 random bits per character. Compare this to a random UUID,
     * which only has 3.4 bits per character in standard layout, and only 122 random bits in total </p>
     *
     * @return a random number in base32 (in string format)
     */
    public static String newId() {
        String id = new BigInteger(130, RANDOM).toString(32);

        return StringUtils.leftPad(id, 26, '0');
    }

    public static String randomUuid() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ENGLISH);
    }

    public static String idFromExternalId(String externalEntityId) {
        return UUID.nameUUIDFromBytes(externalEntityId.getBytes()).toString()
                .replace("-", "")
                .toLowerCase(Locale.ENGLISH)
                .substring(0, 26);
    }

    public  String random13ByteHexGenerator() {
        byte[] bytes = new byte[13];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(26);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
