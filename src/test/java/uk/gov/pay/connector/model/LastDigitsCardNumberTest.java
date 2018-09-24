package uk.gov.pay.connector.model;

import org.junit.Assert;
import org.junit.Test;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

public class LastDigitsCardNumberTest {


    @Test
    public void shouldConvertValidLastFourDigitsOfCard() {
        Assert.assertThat(LastDigitsCardNumber.of("1234").toString(), is("1234"));
        Assert.assertThat(LastDigitsCardNumber.ofNullable("1234").toString(), is("1234"));
    }

    @Test
    public void shouldReturnNullIfStringIsNull() {
        Assert.assertThat(LastDigitsCardNumber.ofNullable(null), is(nullValue()));
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
