package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class GooglePayPaymentInfoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDeserialisation() throws JsonProcessingException {
        var json = """
                {
                    "accept_header": "text/html;q=1.0, */*;q=0.9",
                    "user_agent_header": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:153.0) Gecko/20100101 Firefox/153.0",
                    "ip_address": "203.0.113.1",
                    "js_enabled": true,
                    "js_navigator_language": "en-GB",
                    "js_screen_color_depth": 32,
                    "js_screen_height": 982,
                    "js_screen_width": 1512,
                    "js_timezone_offset_mins": -60
                }
                """;

        GooglePayPaymentInfo result = objectMapper.readValue(json, GooglePayPaymentInfo.class);

        assertThat(result.getBrowserAcceptHeader(), is(Optional.of("text/html;q=1.0, */*;q=0.9")));
        assertThat(result.getBrowserUserAgent(), is(Optional.of("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:153.0) Gecko/20100101 Firefox/153.0")));
        assertThat(result.getBrowserIpAddress(), is(Optional.of("203.0.113.1")));
        assertThat(result.getBrowserJavaScriptEnabled(), is(Optional.of(Boolean.TRUE)));
        assertThat(result.getBrowserLanguage(), is(Optional.of("en-GB")));
        assertThat(result.getBrowserColorDepth(), is(Optional.of("32")));
        assertThat(result.getBrowserScreenHeight(), is(Optional.of("982")));
        assertThat(result.getBrowserScreenWidth(), is(Optional.of("1512")));
        assertThat(result.getBrowserTZ(), is(Optional.of("-60")));
    }

    @Test
    void testDeserialisationWithSomeMissingValues() throws JsonProcessingException {
        var json = """
                {
                    "accept_header": "text/html;q=1.0, */*;q=0.9",
                    "user_agent_header": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:153.0) Gecko/20100101 Firefox/153.0",
                    "ip_address": "203.0.113.1"
                }
                """;

        GooglePayPaymentInfo result = objectMapper.readValue(json, GooglePayPaymentInfo.class);

        assertThat(result.getBrowserAcceptHeader(), is(Optional.of("text/html;q=1.0, */*;q=0.9")));
        assertThat(result.getBrowserUserAgent(), is(Optional.of("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:153.0) Gecko/20100101 Firefox/153.0")));
        assertThat(result.getBrowserIpAddress(), is(Optional.of("203.0.113.1")));
        assertThat(result.getBrowserJavaScriptEnabled(), is(Optional.empty()));
        assertThat(result.getBrowserLanguage(), is(Optional.empty()));
        assertThat(result.getBrowserColorDepth(), is(Optional.empty()));
        assertThat(result.getBrowserScreenHeight(), is(Optional.empty()));
        assertThat(result.getBrowserScreenWidth(), is(Optional.empty()));
        assertThat(result.getBrowserTZ(), is(Optional.empty()));
    }

}
