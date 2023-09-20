package uk.gov.pay.connector.wallets.googlepay.api;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class WorldpayGooglePayAuthRequestTest {

    @Test
    void shouldDeserializeFromJsonCorrectly() throws IOException {
        ObjectMapper objectMapper = Jackson.getObjectMapper();
        JsonNode expected = objectMapper.readTree(fixture("googlepay/example-3ds-auth-request.json"));
        WorldpayGooglePayAuthRequest actual = objectMapper.readValue(
                fixture("googlepay/example-3ds-auth-request.json"), WorldpayGooglePayAuthRequest.class);

        JsonNode paymentInfo = expected.get("payment_info");
        assertThat(actual.getPaymentInfo().getCardholderName(), is(paymentInfo.get("cardholder_name").asText()));
        assertThat(actual.getPaymentInfo().getLastDigitsCardNumber(), is(paymentInfo.get("last_digits_card_number").asText()));
        assertThat(actual.getPaymentInfo().getBrand(), is(paymentInfo.get("brand").asText()));
        assertThat(actual.getPaymentInfo().getEmail(), is(paymentInfo.get("email").asText()));
        assertThat(actual.getPaymentInfo().getAcceptHeader(), is(paymentInfo.get("accept_header").asText()));
        assertThat(actual.getPaymentInfo().getUserAgentHeader(), is(paymentInfo.get("user_agent_header").asText()));
        assertThat(actual.getPaymentInfo().getIpAddress(), is(paymentInfo.get("ip_address").asText()));

        JsonNode encryptedPaymentData = expected.get("encrypted_payment_data");
        assertThat(actual.getEncryptedPaymentData().getSignature(), is(encryptedPaymentData.get("signature").asText()));
        assertThat(actual.getEncryptedPaymentData().getProtocolVersion(), is(encryptedPaymentData.get("protocol_version").asText()));
        assertThat(actual.getEncryptedPaymentData().getSignedMessage(), is(encryptedPaymentData.get("signed_message").asText()));
    }
}
