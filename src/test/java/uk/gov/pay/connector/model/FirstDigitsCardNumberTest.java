package uk.gov.pay.connector.model;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class FirstDigitsCardNumberTest {


    @Test
    public void shouldConvertValidFirstSixDigitsOfCard() {
        assertThat(FirstDigitsCardNumber.of("123456").toString(), is("123456"));
        assertThat(FirstDigitsCardNumber.ofNullable("123456").toString(), is("123456"));
    }

    @Test
    public void shouldReturnNullIfStringIsNull() {
        assertThat(FirstDigitsCardNumber.ofNullable(null), is(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowIfNonNumericFirstSixDigitsOfCard() {
        FirstDigitsCardNumber.of("a23442");
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowIfNotSixDigitsOfCard() {
        FirstDigitsCardNumber.of("22345");
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowIfNullSixDigitsOfCard() {
        FirstDigitsCardNumber.of(null);
    }

}
