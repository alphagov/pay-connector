package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayCredentials;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayMerchantCodeCredentials;
import uk.gov.service.payments.commons.api.exception.ValidationException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GatewayAccountCredentialsRequestValidatorTest {

    @Mock
    private ConnectorConfiguration connectorConfiguration;
    @Mock
    private WorldpayConfig worldpayConfig;
    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private GatewayConfig gatewayConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GatewayAccountCredentialsRequestValidator validator = new GatewayAccountCredentialsRequestValidator();

    @Test
    void shouldNotThrowWithValidStripeCreateRequest() {
        var request = new GatewayAccountCredentialsRequest("stripe", Map.of(
                "stripe_account_id", "acct_something"));
        assertDoesNotThrow(() -> validator.validateCreate(request));
    }

    @Test
    void shouldThrowWhenCreateHasPaymentProviderIsMissing() {
        var request = new GatewayAccountCredentialsRequest(null, null);
        var thrown = assertThrows(ValidationException.class, () -> validator.validateCreate(request));
        assertThat(thrown.getErrors().get(0), is("Field(s) missing: [payment_provider]"));
    }

    @Test
    void shouldThrowWhenCreateRequestPaymentProviderIsNotStripeOrWorldpay() {
        var request = new GatewayAccountCredentialsRequest("sandbox", null);
        var thrown = assertThrows(ValidationException.class, () -> validator.validateCreate(request));
        assertThat(thrown.getErrors().get(0), is("Operation not supported for payment provider 'sandbox'"));
    }

    @Test
    void shouldThrowWhenWorldpayCreateRequestHasCredentialsField() {
        var request = new GatewayAccountCredentialsRequest("worldpay", Map.of(
                "username", "username",
                "password", "password"
        ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validateCreate(request));
        assertThat(thrown.getErrors().get(0), is("Field [credentials] is not supported for payment provider Worldpay"));
    }

    @Test
    void shouldThrowWhenPatchRequestInvalid() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(Map.of("path", "credentials",
                        "op", "add",
                        "value", "something")));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", new WorldpayCredentials()));
        assertThat(thrown.getErrors().get(0), is("Operation [add] not supported for path [credentials]"));
    }

    @Test
    void shouldThrowWhenRequestWithLegacyReplaceCredentialsPathForWorldpay() {
        Map<String, Object> credentials = Map.of(
                "merchant_id", "some-merchant-id"
        );
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(Map.of("path", "credentials",
                        "op", "replace",
                        "value", credentials)));
        var thrown = assertThrows(UnsupportedOperationException.class, () -> validator.validatePatch(request, "worldpay", new WorldpayCredentials()));
        assertThat(thrown.getMessage(), is("Path [credentials] is not supported for updating Worldpay credentials"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"credentials/worldpay/one_off_customer_initiated", "credentials/worldpay/recurring_customer_initiated", "credentials/worldpay/recurring_merchant_initiated"})
    void shouldThrowWhenFieldsAreIncorrectForWorldpayCredentialsPatchRequest(String path) {
        Map<String, Object> credentials = Map.of(
                "merchant_id", "this-should-be-merchant-code",
                "username", "username",
                "password", "password"
        );
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(Map.of("path", path,
                        "op", "replace",
                        "value", credentials)));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", new WorldpayCredentials()));
        assertThat(thrown.getErrors().get(0), is("Value for path [" + path + "] is missing field(s): [merchant_code]"));
    }

    @Test
    void shouldThrowWhenLastUpdatedByUserExternalIdIsNotAString() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "last_updated_by_user_external_id",
                                "op", "replace",
                                "value", 1)
                ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", new WorldpayCredentials()));
        assertThat(thrown.getErrors().get(0), is("Value for path [last_updated_by_user_external_id] must be a string"));
    }

    @Test
    void shouldThrowWhenStateIsNotAString() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "state",
                                "op", "replace",
                                "value", 1)
                ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", new WorldpayCredentials()));
        assertThat(thrown.getErrors().get(0), is("Value for path [state] must be a string"));
    }

    @Test
    void shouldThrowWhenStateIsNotAllowedValue() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "state",
                                "op", "replace",
                                "value", "ACTIVE")
                ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", new WorldpayCredentials()));
        assertThat(thrown.getErrors().get(0), is("Operation with path [state] can only be used to update state to [VERIFIED_WITH_LIVE_PAYMENT]"));
    }

    @Test
    void shouldNotThrowWhenValidWorldpayPatchRequest() {
        Map<String, Object> worldpayCredentials = Map.of(
                "merchant_code", "some-merchant-code",
                "username", "username",
                "password", "password"
        );

        JsonNode request = objectMapper.valueToTree(
                List.of(
                        Map.of("path", "credentials/worldpay/one_off_customer_initiated",
                                "op", "replace",
                                "value", worldpayCredentials),
                        Map.of("path", "credentials/worldpay/recurring_customer_initiated",
                                "op", "replace",
                                "value", worldpayCredentials),
                        Map.of("path", "credentials/worldpay/recurring_merchant_initiated",
                                "op", "replace",
                                "value", worldpayCredentials),
                        Map.of("path", "last_updated_by_user_external_id",
                                "op", "replace",
                                "value", "a-user-external-id"),
                        Map.of("path", "state",
                                "op", "replace",
                                "value", "VERIFIED_WITH_LIVE_PAYMENT"),
                        Map.of("path", "credentials/gateway_merchant_id",
                                "op", "replace",
                                "value", "abcdef123abcdef")
                ));
        var existingCredentials = new WorldpayCredentials();
        existingCredentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials());
        assertDoesNotThrow(() -> validator.validatePatch(request, "worldpay", existingCredentials));
    }

    @Test
    void shouldNotThrowWhenValidStripePatchRequest() {
        JsonNode request = objectMapper.valueToTree(
                List.of(
                        Map.of("path", "credentials",
                                "op", "replace",
                                "value", Map.of("stripe_account_id", "acct_something")),
                        Map.of("path", "last_updated_by_user_external_id",
                                "op", "replace",
                                "value", "a-user-external-id"),
                        Map.of("path", "state",
                                "op", "replace",
                                "value", "VERIFIED_WITH_LIVE_PAYMENT")
                ));
        var credentials = mock(GatewayCredentials.class); 
        assertDoesNotThrow(() -> validator.validatePatch(request, "stripe", credentials));
    }

    @Test
    void shouldThrowWhenOperationNotAllowedForGatewayMerchantId() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "credentials/gateway_merchant_id",
                                "op", "add",
                                "value", "abcdef123abcdef")
                ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", new WorldpayCredentials()));
        assertThat(thrown.getErrors().get(0), is("Operation [add] not supported for path [credentials/gateway_merchant_id]"));
    }

    @Test
    void shouldThrowWhenValueIsNotValidForGatewayMerchantId() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "credentials/gateway_merchant_id",
                                "op", "replace",
                                "value", "ABCDEF123abcdef")
                ));
        var credentials = new WorldpayCredentials();
        credentials.setOneOffCustomerInitiatedCredentials(new WorldpayMerchantCodeCredentials());
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", credentials));
        assertThat(thrown.getErrors().get(0), is("Field [credentials/gateway_merchant_id] value [ABCDEF123abcdef] does not match that expected for a Worldpay Merchant ID; should be 15 characters and within range [0-9a-f]"));
    }

    @Test
    void shouldThrowWhenValueIsMissingForGatewayMerchantId() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "credentials/gateway_merchant_id",
                                "op", "replace")
                ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", new WorldpayCredentials()));
        assertThat(thrown.getErrors().get(0), is("Field [value] is required"));
    }

    @Test
    void shouldThrowIfPaymentProviderIsNotWorldpayForGatewayMerchantId() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "credentials/gateway_merchant_id",
                                "op", "replace",
                                "value", "123456789012311")
                ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "stripe", new WorldpayCredentials()));
        assertThat(thrown.getErrors().get(0), is("Gateway 'stripe' does not support digital wallets."));
    }

    @Test
    void shouldThrowIfCredentialsAreEmptyOnGatewayAccountCredentialsForGatewayMerchantId() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "credentials/gateway_merchant_id",
                                "op", "replace",
                                "value", "invalid-value")
                ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", new WorldpayCredentials()));
        assertThat(thrown.getErrors().get(0), is("Account credentials are required to set a Gateway Merchant ID."));
    }
}
