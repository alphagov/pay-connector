package uk.gov.pay.connector.wallets.googlepay.api;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.io.IOException;
import java.util.Set;

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
        assertThat(actual.getEncryptedPaymentData().getType(), is(encryptedPaymentData.get("type").asText()));
        assertThat(actual.getEncryptedPaymentData().getProtocolVersion(), is(encryptedPaymentData.get("protocolVersion").asText()));

        JsonNode signedMessage = encryptedPaymentData.get("signedMessage");
        assertThat(actual.getEncryptedPaymentData().getSignedMessage().getEncryptedMessage(), is(signedMessage.get("encryptedMessage").asText()));
        assertThat(actual.getEncryptedPaymentData().getSignedMessage().getEphemeralPublicKey(), is(signedMessage.get("ephemeralPublicKey").asText()));
        assertThat(actual.getEncryptedPaymentData().getSignedMessage().getTag(), is(signedMessage.get("tag").asText()));

        JsonNode intermediateSigningKey = encryptedPaymentData.get("intermediateSigningKey");
        assertThat(actual.getEncryptedPaymentData().getIntermediateSigningKey().getSignatures().length, is(intermediateSigningKey.get("signatures").size()));
        assertThat(actual.getEncryptedPaymentData().getIntermediateSigningKey().getSignatures()[0], is(intermediateSigningKey.get("signatures").get(0).asText()));

        JsonNode signedKey = intermediateSigningKey.get("signedKey");
        assertThat(actual.getEncryptedPaymentData().getIntermediateSigningKey().getSignedKey().getExpirationDate(), is(signedKey.get("keyExpiration").asText()));
        assertThat(actual.getEncryptedPaymentData().getIntermediateSigningKey().getSignedKey().getKey(), is(signedKey.get("keyValue").asText()));

        JsonNode token = encryptedPaymentData.get("token");
        assertThat(actual.getEncryptedPaymentData().getToken().getSignature(), is(token.get("signature").asText()));
    }

    @Test
    public void shouldPassValidation() throws IOException {
        GooglePayAuthRequest valid = Jackson.getObjectMapper()
                .readValue(fixture("googlepay/example-auth-request.json"), GooglePayAuthRequest.class);
        
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<GooglePayAuthRequest>> errors = validator.validate(valid);
        assertThat(errors.size(), is(0));
    }
}
