package uk.gov.pay.connector.wallets.googlepay.api;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class GooglePayAuthRequestTest {


    @Test
    public void shouldDeserializeFromJsonCorrectly() throws IOException {
        ObjectMapper objectMapper = Jackson.getObjectMapper();
        
        JsonNode expected = objectMapper.readTree(fixture("googlepay/example-auth-request.json"));
        GooglePayAuthRequest actual = objectMapper.readValue(
                fixture("googlepay/example-auth-request.json"), GooglePayAuthRequest.class);

        JsonNode paymentInfo = expected.get("payment_info");
        assertThat(actual.getPaymentInfo().getCardholderName(), is(paymentInfo.get("cardholder_name").asText()));
        assertThat(actual.getPaymentInfo().getLastDigitsCardNumber(), is(paymentInfo.get("last_digits_card_number").asText()));
        assertThat(actual.getPaymentInfo().getBrand(), is(paymentInfo.get("brand").asText()));
        assertThat(actual.getPaymentInfo().getCardType().toString(), is(paymentInfo.get("card_type").asText()));
        assertThat(actual.getPaymentInfo().getEmail(), is(paymentInfo.get("email").asText()));

        JsonNode encryptedPaymentData = expected.get("encrypted_payment_data");
        assertThat(actual.getEncryptedPaymentData().getSignature(), is(encryptedPaymentData.get("signature").asText()));
        assertThat(actual.getEncryptedPaymentData().getProtocolVersion(), is(encryptedPaymentData.get("protocol_version").asText()));
        assertThat(actual.getEncryptedPaymentData().getSignedMessage(), is(encryptedPaymentData.get("signed_message").asText()));
        
    }
}
