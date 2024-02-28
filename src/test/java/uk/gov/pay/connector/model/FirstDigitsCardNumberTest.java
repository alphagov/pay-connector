package uk.gov.pay.connector.model;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.card.model.FirstDigitsCardNumber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FirstDigitsCardNumberTest {

    @Test
    void shouldConvertSixDigits() {
        assertThat(FirstDigitsCardNumber.of("123456").toString(), is("123456"));
    }

    @Test
    void shouldThrowIfNotArabicNumerals() {
        assertThrows(IllegalArgumentException.class, () -> FirstDigitsCardNumber.of("१२३४५६"));
    }

    @Test
    void shouldThrowIfNonNumeric() {
        assertThrows(IllegalArgumentException.class, () -> FirstDigitsCardNumber.of("1a3456"));
    }

    @Test
    void shouldThrowIfFewerThanSixDigits() {
        assertThrows(IllegalArgumentException.class, () -> FirstDigitsCardNumber.of("12345"));
    }

    @Test
    void shouldThrowIfMoreThanSixDigits() {
        assertThrows(IllegalArgumentException.class, () -> FirstDigitsCardNumber.of("1234567"));
    }

    @Test
    void shouldThrowIfPrecedingCharacters() {
        assertThrows(IllegalArgumentException.class, () -> FirstDigitsCardNumber.of(" 123456"));
    }

    @Test
    void shouldThrowIfTrailingCharacters() {
        assertThrows(IllegalArgumentException.class, () -> FirstDigitsCardNumber.of("123456 "));
    }

    @Test
    void shouldThrowIfEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> FirstDigitsCardNumber.of(""));
    }

    @Test
    void shouldThrowIfNull() {
        assertThrows(NullPointerException.class, () -> FirstDigitsCardNumber.of(null));
    }

}
