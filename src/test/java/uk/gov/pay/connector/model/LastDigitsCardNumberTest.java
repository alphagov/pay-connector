package uk.gov.pay.connector.model;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class LastDigitsCardNumberTest {


    @Test
    public void shouldConvertValidLastFourDigitsOfCard() {
        assertThat(LastDigitsCardNumber.of("1234").toString(), is("1234"));
        assertThat(LastDigitsCardNumber.ofNullable("1234").toString(), is("1234"));
    }

    @Test
    public void shouldReturnNullIfStringIsNull() {
        assertThat(LastDigitsCardNumber.ofNullable(null), is(nullValue()));
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowIfNonNumericLastFourDigitsOfCard() {
        LastDigitsCardNumber.of("a234");
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowIfNotFourDigitsOfCard() {
        LastDigitsCardNumber.of("24");
    }

    @Test(expected = RuntimeException.class)
    public void shouldThrowIfNullFourDigitsOfCard() {
        LastDigitsCardNumber.of(null);
    }

}
