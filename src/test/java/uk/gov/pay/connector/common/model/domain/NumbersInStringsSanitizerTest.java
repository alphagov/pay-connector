package uk.gov.pay.connector.common.model.domain;


import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;

class NumbersInStringsSanitizerTest {

    @Test
    void sanitize_shouldSanitizeNumbersAsStarsInStringWith11Numbers() {

        String sanitizedValue = NumbersInStringsSanitizer.sanitize("01234567892");

        assertThat(sanitizedValue, is("***********"));
    }

    @Test
    void sanitize_shouldNotSanitizeNumbersAsStarsInStringWith10Numbers() {

        String sanitizedValue = NumbersInStringsSanitizer.sanitize("2345678912");

        assertThat(sanitizedValue, is("2345678912"));
    }

    @Test
    void sanitize_shouldNotSanitizeABlankString() {

        String sanitizedValue = NumbersInStringsSanitizer.sanitize("  ");

        assertThat(sanitizedValue, is("  "));
    }

    @Test
    void sanitize_shouldNotSanitizeANullValue() {

        String sanitizedValue = NumbersInStringsSanitizer.sanitize(null);

        assertThat(sanitizedValue, is(nullValue()));
    }

    @Test
    void sanitize_shouldSanitizeStringWithDigitsInRandomPositions() {

        String sanitizedValue = NumbersInStringsSanitizer.sanitize("&%)9(&%^&*988   hjdjkd$%12  **2221opsdja9q8987^&*88&6^f99s*%^");

        assertThat(sanitizedValue, is("&%)*(&%^&****   hjdjkd$%**  ******opsdja*q****^&***&*^f**s*%^"));
    }
}
