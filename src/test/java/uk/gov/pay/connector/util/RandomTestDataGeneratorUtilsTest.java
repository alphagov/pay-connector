package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.randomAlphabetic;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.randomAlphanumeric;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomInt;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;

class RandomTestDataGeneratorUtilsTest {

    @Test
    void randomAlphabetic_positiveLength_producesOnlyLettersAndCorrectLength() {
        int length = 10;
        String randomString = randomAlphabetic(length);
        assertNotNull(randomString);
        assertEquals(length, randomString.length());
        assertTrue(randomString.chars().allMatch(Character::isLetterOrDigit),
                "expected only letters or digits but found: " + randomString);

    }

    @Test
    void randomAlphabetic_zeroLength_returnsEmptyString() {
        String randomString = randomAlphabetic(0);
        assertNotNull(randomString);
        assertEquals(0, randomString.length());
    }

    @Test
    void randomAlphaNumeric_positiveLength_producesLettersOrDigitsAndCorrectLength() {
        int length = 12;
        String randomString = randomAlphanumeric(length);
        assertNotNull(randomString);
        assertEquals(length, randomString.length());
        assertTrue(randomString.chars().allMatch(Character::isLetterOrDigit),
                "expected only letters or digits but found: " + randomString);
    }

    @Test
    void randomAlphaNumeric_zeroLength_returnsEmptyString() {
        String string = randomAlphanumeric(0);
        assertNotNull(string);
        assertEquals(0, string.length());
    }


    @Test
    void randomLong_defaultRange_withinExpectedBounds() {
        long randomLong = secureRandomLong();
        assertTrue(randomLong >= 0, "value should be >= 0");
        assertTrue(randomLong < Long.MAX_VALUE, "value should be < Long.MAX_VALUE");
    }


    @Test
    void randomLong_singleValueRange_returnsThatValue() {
        long min = 5L;
        long max = 6L; // only possible value is 5
        long randomLong = secureRandomLong(min, max);
        assertEquals(min, randomLong);
    }


    @Test
    void randomInt_defaultRange_withinExpectedBounds() {
        long randomInt = secureRandomInt();
        assertTrue(randomInt >= 0, "value should be >= 0");
        assertTrue(randomInt < Integer.MAX_VALUE, "value should be < Integer.MAX_VALUE");
    }


    @Test
    void randomInt_singleValueRange_returnsThatValue() {
        int min = 7;
        int max = 8; // only possible value is 7
        long randomLong = secureRandomInt(min, max);
        assertEquals(min, randomLong);
    }

}
