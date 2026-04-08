package uk.gov.pay.connector.gateway.worldpay;

import io.github.netmikey.logunit.api.LogCapturer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse.WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITHOUT_TRANSACTION_IDENTIFIER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITH_TRANSACTION_IDENTIFIER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE_WITH_INVALID_EXPIRY_YEAR;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE_WITH_MISSING_EXPIRY_DATE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class WorldpayOrderStatusResponseTest {

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(WorldpayOrderStatusResponse.class);

    @ParameterizedTest
    @ValueSource(strings = {"OUT_OF_SCOPE", "REJECTED"})
    void worldpay_response_should_be_soft_decline(String exemptionResponseResult) {
        var worldpayOrderStatusResponse = new WorldpayOrderStatusResponse();
        worldpayOrderStatusResponse.setLastEvent("REFUSED");
        worldpayOrderStatusResponse.setExemptionResponseResult(exemptionResponseResult);

        assertTrue(worldpayOrderStatusResponse.isSoftDecline());
    }

    @Test
    void worldpay_response_should_not_be_soft_decline() {
        var worldpayOrderStatusResponse = new WorldpayOrderStatusResponse();
        worldpayOrderStatusResponse.setLastEvent("REFUSED");
        worldpayOrderStatusResponse.setExemptionResponseResult("HONOURED");

        assertFalse(worldpayOrderStatusResponse.isSoftDecline());
    }

    @Test
    void worldpay_response_should_not_be_soft_decline_when_error() {
        var worldpayOrderStatusResponse = new WorldpayOrderStatusResponse();
        worldpayOrderStatusResponse.setLastEvent("ERROR");
        worldpayOrderStatusResponse.setExemptionResponseResult("REFUSED");

        assertFalse(worldpayOrderStatusResponse.isSoftDecline());
    }

    @Test
    void should_get_recurring_payment_token_with_transaction_scheme_identifier() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITH_TRANSACTION_IDENTIFIER);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);

        assertThat(worldpayOrderStatusResponse.getGatewayRecurringAuthToken().isPresent(), is(true));

        Map<String, String> gatewayRecurringAuthToken = worldpayOrderStatusResponse.getGatewayRecurringAuthToken().get();
        assertThat(gatewayRecurringAuthToken, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "9961191959944156907"));
        assertThat(gatewayRecurringAuthToken, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY, "1234567890"));
    }

    @Test
    void should_get_recurring_payment_token_without_transaction_scheme_identifier() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_CREATE_TOKEN_SUCCESS_RESPONSE_WITHOUT_TRANSACTION_IDENTIFIER);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);

        assertThat(worldpayOrderStatusResponse.getGatewayRecurringAuthToken().isPresent(), is(true));

        Map<String, String> gatewayRecurringAuthToken = worldpayOrderStatusResponse.getGatewayRecurringAuthToken().get();
        assertThat(gatewayRecurringAuthToken, hasEntry(WORLDPAY_RECURRING_AUTH_TOKEN_PAYMENT_TOKEN_ID_KEY, "9961191959944156907"));
        assertThat(gatewayRecurringAuthToken, not(hasKey(WORLDPAY_RECURRING_AUTH_TOKEN_TRANSACTION_IDENTIFIER_KEY)));
    }

    @Test
    void get_recurring_payment_token_should_return_empty_optional_when_no_token_id_in_response() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);

        assertThat(worldpayOrderStatusResponse.getGatewayRecurringAuthToken().isPresent(), is(false));
    }

    @Test
    void WorldpayAuthorisationRejectedCode_should_be_mapped_to_MappedAuthorisationRejectedReason_in_stringified_response() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_FAILED_RESPONSE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);
        assertTrue(worldpayOrderStatusResponse.toString().contains("Mapped rejection reason: DO_NOT_HONOUR"));
    }

    @Test
    void get_expiry_date_should_return_correctly_when_expiry_date_fields_are_present() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);
        assertThat(worldpayOrderStatusResponse.getCardExpiryDate().isPresent(), is(true));
        assertThat(worldpayOrderStatusResponse.getCardExpiryDate().get(), is(CardExpiryDate.valueOf("11/35")));
    }

    @Test
    void get_expiry_date_should_return_empty_optional_but_no_log_when_expiry_date_fields_are_absent_from_failed_response() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_FAILED_RESPONSE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);

        assertThat(worldpayOrderStatusResponse.getCardExpiryDate().isPresent(), is(false));
        Assertions.assertThat(logs.size())
                .isZero();
    }

    @Test
    void get_expiry_date_should_return_empty_optional_and_log_when_expiry_date_fields_are_absent_from_success_response() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE_WITH_MISSING_EXPIRY_DATE);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);
        assertThat(worldpayOrderStatusResponse.getCardExpiryDate().isPresent(), is(false));
        verifyLogging(1, "Expiry date is not included in Worldpay wallet payment authorisation success response.");
    }

    @Test
    void get_expiry_date_should_return_empty_optional_and_log_when_expiry_date_is_invalid() throws Exception {
        String response = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE_WITH_INVALID_EXPIRY_YEAR);
        WorldpayOrderStatusResponse worldpayOrderStatusResponse = XMLUnmarshaller.unmarshall(response, WorldpayOrderStatusResponse.class);
        assertThat(worldpayOrderStatusResponse.getCardExpiryDate().isPresent(), is(false));
        verifyLogging(1, "Expiry date in Worldpay wallet payment authorisation response is in an unexpected format; month has 2 digits, year has 3 digits.");
    }

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, nullValues = "null", textBlock = """
                refusedReturnCodeDescription, refusedReturnCode, outcome, present
                fraudulent, 42, 42 fraudulent, true
                fraudulent, null, fraudulent, true
                null, 42, 42, true
                null, null, null, false
            """)
    void get_gateway_rejection_reason_should_return_reduced_values(String refusedReturnCodeDescription, String refusedReturnCode, String outcome, Boolean present) throws Exception {
        var response = spy(WorldpayOrderStatusResponse.class);
        when(response.getRefusedReturnCode()).thenReturn(refusedReturnCode);
        when(response.getRefusedReturnCodeDescription()).thenReturn(refusedReturnCodeDescription);
        Optional<String> gatewayRejectionReason = response.getGatewayRejectionReason();
        assertThat(gatewayRejectionReason.isPresent(), is(present));
        if (present) {
            assertThat(gatewayRejectionReason.get(), is(outcome));
        }
    }

    private void verifyLogging(int invocations, String logMessage) {
        Assertions.assertThat(logs.size())
                .isEqualTo(invocations);
        logs.assertContains(logMessage);
    }
}
