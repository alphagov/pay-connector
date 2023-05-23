package uk.gov.pay.connector.gatewayaccountcredentials.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest;
import uk.gov.service.payments.commons.api.exception.ValidationException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private GatewayAccountCredentialsRequestValidator validator;

    @BeforeEach
    void before() {
        when(worldpayConfig.getCredentials()).thenReturn(List.of("merchant_id"));
        when(gatewayConfig.getCredentials()).thenReturn(List.of());
        when(stripeGatewayConfig.getCredentials()).thenReturn(List.of());
        when(connectorConfiguration.getWorldpayConfig()).thenReturn(worldpayConfig);
        when(connectorConfiguration.getEpdqConfig()).thenReturn(gatewayConfig);
        when(connectorConfiguration.getStripeConfig()).thenReturn(stripeGatewayConfig);

        validator = new GatewayAccountCredentialsRequestValidator(connectorConfiguration);
    }

    @Test
    void shouldNotThrowWithValidRequest() {
        var request = new GatewayAccountCredentialsRequest("worldpay", Map.of("merchant_id", "some-merchant-id"));
        assertDoesNotThrow(() -> validator.validateCreate(request));
    }

    @Test
    void shouldThrowWhenPaymentProviderIsMissing() {
        var request = new GatewayAccountCredentialsRequest(null, null);
        var thrown = assertThrows(ValidationException.class, () -> validator.validateCreate(request));
        assertThat(thrown.getErrors().get(0), is("Field(s) missing: [payment_provider]"));
    }

    @Test
    void shouldThrowWhenPaymentProviderIsNotStripeOrWorldpay() {
        var request = new GatewayAccountCredentialsRequest("sandbox", null);
        var thrown = assertThrows(ValidationException.class, () -> validator.validateCreate(request));
        assertThat(thrown.getErrors().get(0), is("Operation not supported for payment provider 'sandbox'"));
    }

    @Test
    void shouldThrowWhenCredentialsAreMissing() {
        var request = new GatewayAccountCredentialsRequest("worldpay", Map.of("missing_merchant_id", "some-merchant-id"));
        var thrown = assertThrows(ValidationException.class, () -> validator.validateCreate(request));
        assertThat(thrown.getErrors().get(0), is("Field(s) missing: [merchant_id]"));
    }

    @Test
    void shouldThrowWhenPatchRequestInvalid() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(Map.of("path", "credentials",
                        "op", "add",
                        "value", "something")));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", Map.of()));
        assertThat(thrown.getErrors().get(0), is("Operation [add] not supported for path [credentials]"));
    }

    @Test
    void shouldThrowWhenCredentialsFieldsAreMissingFromPatchRequest() {
        Map<String, Object> credentials = Map.of(
                "missing_merchant_id", "some-merchant-id"
        );
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(Map.of("path", "credentials",
                        "op", "replace",
                        "value", credentials)));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", Map.of()));
        assertThat(thrown.getErrors().get(0), is("Value for path [credentials] is missing field(s): [merchant_id]"));
    }

    @Test
    void shouldThrowWhenLastUpdatedByUserExternalIdIsNotAString() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "last_updated_by_user_external_id",
                                "op", "replace",
                                "value", 1)
                ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", Map.of()));
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
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", Map.of()));
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
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", Map.of()));
        assertThat(thrown.getErrors().get(0), is("Operation with path [state] can only be used to update state to [VERIFIED_WITH_LIVE_PAYMENT]"));
    }

    @Test
    void shouldNotThrowWhenValidPatchRequest() {
        Map<String, Object> credentials = Map.of(
                "merchant_id", "some-merchant-id"
        );
        JsonNode request = objectMapper.valueToTree(
                List.of(
                        Map.of("path", "credentials",
                                "op", "replace",
                                "value", credentials),
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
        assertDoesNotThrow(() -> validator.validatePatch(request, "worldpay", Map.of("cred-key1", "cred-value-1")));
    }

    @Test
    void shouldThrowWhenOperationNotAllowedForGatewayMerchantId() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "credentials/gateway_merchant_id",
                                "op", "add",
                                "value", "abcdef123abcdef")
                ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", Map.of()));
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
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", Map.of("cred-key-1", "cred-value-1")));
        assertThat(thrown.getErrors().get(0), is("Field [credentials/gateway_merchant_id] value [ABCDEF123abcdef] does not match that expected for a Worldpay Merchant ID; should be 15 characters and within range [0-9a-f]"));
    }

    @Test
    void shouldThrowWhenValueIsMissingForGatewayMerchantId() {
        JsonNode request = objectMapper.valueToTree(
                Collections.singletonList(
                        Map.of("path", "credentials/gateway_merchant_id",
                                "op", "replace")
                ));
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", Map.of()));
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
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "stripe", Map.of()));
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
        var thrown = assertThrows(ValidationException.class, () -> validator.validatePatch(request, "worldpay", Map.of()));
        assertThat(thrown.getErrors().get(0), is("Account credentials are required to set a Gateway Merchant ID."));
    }
}
