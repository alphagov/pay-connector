package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class AdyenGooglePayAuthRequestTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String JSON = """
        {
            "payment_info": {
                "last_digits_card_number": "4242",
                "brand": "visa",
                "cardholder_name": "Example Name",
                "email": "example@test.example",
                "accept_header": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,/;q=0.8",
                "user_agent_header": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.183 Safari/537.36",
                "ip_address": "8.8.8.8"
            },
            "token": "abcd"
    }""";
    
    @Test
    void shouldDeserializeFromJsonCorrectly() throws JsonProcessingException {
        AdyenGooglePayAuthRequest result = objectMapper.readValue(JSON, AdyenGooglePayAuthRequest.class);

        assertThat(result.getPaymentInfo().getCardholderName(), is("Example Name"));
        assertThat(result.getPaymentInfo().getLastDigitsCardNumber(), is("4242"));
        assertThat(result.getPaymentInfo().getBrand(), is("visa"));
        assertThat(result.getPaymentInfo().getBrowserAcceptHeader().isPresent(), is(true));
        assertThat(result.getPaymentInfo().getBrowserAcceptHeader().get(), is("text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,/;q=0.8"));

        assertThat(result.token(), is("abcd"));
    }
}
