package uk.gov.pay.connector.model;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.card.model.LastDigitsCardNumber;

import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LastDigitsCardNumberTest {

    @Test
    void shouldConvertSixDigits() {
        MatcherAssert.assertThat(LastDigitsCardNumber.of("1234").toString(), is("1234"));
    }

    @Test
    void shouldThrowIfNotArabicNumerals() {
        assertThrows(IllegalArgumentException.class, () -> LastDigitsCardNumber.of("౧౨౩౪"));
    }

    @Test
    void shouldThrowIfNonNumeric() {
        assertThrows(IllegalArgumentException.class, () -> LastDigitsCardNumber.of("1a34"));
    }

    @Test
    void shouldThrowIfFewerThanFourDigits() {
        assertThrows(IllegalArgumentException.class, () -> LastDigitsCardNumber.of("123"));
    }

    @Test
    void shouldThrowIfMoreThanFourDigits() {
        assertThrows(IllegalArgumentException.class, () -> LastDigitsCardNumber.of("12345"));
    }

    @Test
    void shouldThrowIfPrecedingCharacters() {
        assertThrows(IllegalArgumentException.class, () -> LastDigitsCardNumber.of(" 1234"));
    }

    @Test
    void shouldThrowIfTrailingCharacters() {
        assertThrows(IllegalArgumentException.class, () -> LastDigitsCardNumber.of("1234 "));
    }

    @Test
    void shouldThrowIfEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> LastDigitsCardNumber.of(""));
    }

    @Test
    void shouldThrowIfNull() {
        assertThrows(NullPointerException.class, () -> LastDigitsCardNumber.of(null));
    }

}
