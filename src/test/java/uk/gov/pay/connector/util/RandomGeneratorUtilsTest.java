package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.util.RandomGeneratorUtils.*;

class RandomGeneratorUtilsTest {

    @Test
    void randomAlphabetic_positiveLength_producesOnlyLettersAndCorrectLength() {
        int length = 10;
        String s = randomAlphabetic(length);
        assertNotNull(s);
        assertEquals(length, s.length());
        for (char c : s.toCharArray()) {
            assertTrue(Character.isLetter(c), "expected only letters but found: " + c);
        }
    }

    @Test
    void randomAlphabetic_zeroLength_returnsEmptyString() {
        String s = randomAlphabetic(0);
        assertNotNull(s);
        assertEquals(0, s.length());
    }

    @Test
    void randomAlphabetic_negativeLength_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> randomAlphabetic(-1));
    }

    @Test
    void randomAlphaNumeric_positiveLength_producesLettersOrDigitsAndCorrectLength() {
        int length = 12;
        String s = randomAlphanumeric(length);
        assertNotNull(s);
        assertEquals(length, s.length());
        for (char c : s.toCharArray()) {
            assertTrue(Character.isLetterOrDigit(c), "expected only letters or digits but found: " + c);
        }
    }

    @Test
    void randomAlphaNumeric_zeroLength_returnsEmptyString() {
        String s = randomAlphanumeric(0);
        assertNotNull(s);
        assertEquals(0, s.length());
    }

    @Test
    void randomAlphaNumeric_negativeLength_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> randomAlphanumeric(-5));
    }

    @Test
    void randomLong_defaultRange_withinExpectedBounds() {
        long v = randomLong();
        assertTrue(v >= 0, "value should be >= 0");
        assertTrue(v < Long.MAX_VALUE, "value should be < Long.MAX_VALUE");
    }

    @Test
    void randomLong_withBounds_returnsValueWithinProvidedRange() {
        long minInclusive = -10L;
        long maxExclusive = 0L;
        for (int i = 0; i < 50; i++) {
            long v = randomLong(minInclusive, maxExclusive);
            assertTrue(v >= minInclusive, "value should be >= minInclusive");
            assertTrue(v < maxExclusive, "value should be < maxExclusive");
        }
    }

    @Test
    void randomLong_singleValueRange_returnsThatValue() {
        long min = 5L;
        long max = 6L; // only possible value is 5
        long v = randomLong(min, max);
        assertEquals(min, v);
    }

    @Test
    void randomLong_invalidRange_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> randomLong(10L, 10L));
        assertThrows(IllegalArgumentException.class, () -> randomLong(11L, 10L));
    }

    @Test
    void randomInt_defaultRange_withinExpectedBounds() {
        long v = randomInt();
        assertTrue(v >= 0, "value should be >= 0");
        assertTrue(v < Integer.MAX_VALUE, "value should be < Integer.MAX_VALUE");
    }

    @Test
    void randomInt_withBounds_returnsValueWithinProvidedRange() {
        int minInclusive = -20;
        int maxExclusive = 0;
        for (int i = 0; i < 50; i++) {
            long v = randomInt(minInclusive, maxExclusive);
            assertTrue(v >= minInclusive, "value should be >= minInclusive");
            assertTrue(v < maxExclusive, "value should be < maxExclusive");
        }
    }

    @Test
    void randomInt_singleValueRange_returnsThatValue() {
        int min = 7;
        int max = 8; // only possible value is 7
        long v = randomInt(min, max);
        assertEquals(min, v);
    }

    @Test
    void randomInt_invalidRange_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> randomInt(5, 5));
        assertThrows(IllegalArgumentException.class, () -> randomInt(6, 5));
    }
}
