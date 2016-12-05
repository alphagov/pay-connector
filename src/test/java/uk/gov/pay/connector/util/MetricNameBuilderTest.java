package uk.gov.pay.connector.util;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class MetricNameBuilderTest {

    @Test
    public void shouldReturnMatchedUrlPattern() {
        String inputUrl = "/v1/api/accounts/111/charges";
        String expected = "/v1/api/accounts/{resource-id}/charges";
        assertThat(MetricNameBuilder.getMetricsNamespace(inputUrl), is(expected));
    }

    @Test
    public void shouldReturnMatchedUrlPatternForAlphaNumeric() {
        String inputUrl = "/v1/frontend/charges/bbj8r4i2in2jtge5hiovd5a5vv";
        String expected = "/v1/frontend/charges/{resource-id}";
        assertThat(MetricNameBuilder.getMetricsNamespace(inputUrl), is(expected));
    }
}
