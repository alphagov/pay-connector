package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

class GooglePayAuthRequestTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Test
    void shouldDeserializeFromJsonCorrectly() throws JsonProcessingException {
        JsonNode expected = objectMapper.readTree(load("googlepay/example-3ds-auth-request.json"));
        GooglePayAuthRequest actual = objectMapper.readValue(
                load("googlepay/example-3ds-auth-request.json"), GooglePayAuthRequest.class);

        JsonNode paymentInfo = expected.get("payment_info");
        assertThat(actual.getPaymentInfo().getCardholderName(), is(paymentInfo.get("cardholder_name").asText()));
        assertThat(actual.getPaymentInfo().getLastDigitsCardNumber(), is(paymentInfo.get("last_digits_card_number").asText()));
        assertThat(actual.getPaymentInfo().getBrand(), is(paymentInfo.get("brand").asText()));
        assertThat(actual.getPaymentInfo().getEmail(), is(paymentInfo.get("email").asText()));
        assertThat(actual.getPaymentInfo().getBrowserAcceptHeader().isPresent(), is(true));
        assertThat(actual.getPaymentInfo().getBrowserAcceptHeader().get(), is(paymentInfo.get("accept_header").asText()));
        assertThat(actual.getPaymentInfo().getBrowserUserAgent().isPresent(), is(true));
        assertThat(actual.getPaymentInfo().getBrowserUserAgent().get(), is(paymentInfo.get("user_agent_header").asText()));
        assertThat(actual.getPaymentInfo().getBrowserIpAddress().isPresent(), is(true));
        assertThat(actual.getPaymentInfo().getBrowserIpAddress().get(), is(paymentInfo.get("ip_address").asText()));

        JsonNode encryptedPaymentData = expected.get("encrypted_payment_data");
        assertThat(actual.getEncryptedPaymentData().isPresent(), is(true));
        assertThat(actual.getEncryptedPaymentData().get().getSignature(), is(encryptedPaymentData.get("signature").asText()));
        assertThat(actual.getEncryptedPaymentData().get().getProtocolVersion(), is(encryptedPaymentData.get("protocol_version").asText()));
        assertThat(actual.getEncryptedPaymentData().get().getSignedMessage(), is(encryptedPaymentData.get("signed_message").asText()));
    }
}
