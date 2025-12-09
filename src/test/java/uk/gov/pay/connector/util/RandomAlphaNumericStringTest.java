package uk.gov.pay.connector.util;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RandomAlphaNumericStringTest {

    @Test
    void randomAlphabetic_returnsRequestedLengthAndAlphabeticChars() {
        int length = 10;
        String s = RandomAlphaNumericString.randomAlphabetic(length);

        assertThat(s.length(), is(length));
        assertThat(s, matchesPattern("^[A-Za-z]{10}$"));
    }

    @Test
    void randomAlphaNumeric_returnsRequestedLengthAndAlphaNumericChars() {
        int length = 12;
        String s = RandomAlphaNumericString.randomAlphaNumeric(length);

        assertThat(s.length(), is(length));
        assertThat(s, matchesPattern("^[A-Za-z0-9]{12}$"));
    }

    @Test
    void zeroLength_returnsEmptyString() {
        assertThat(RandomAlphaNumericString.randomAlphabetic(0), is(""));
        assertThat(RandomAlphaNumericString.randomAlphaNumeric(0), is(""));
    }

    @Test
    void negativeLength_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> RandomAlphaNumericString.randomAlphabetic(-1));
        assertThrows(IllegalArgumentException.class, () -> RandomAlphaNumericString.randomAlphaNumeric(-5));
    }
}
