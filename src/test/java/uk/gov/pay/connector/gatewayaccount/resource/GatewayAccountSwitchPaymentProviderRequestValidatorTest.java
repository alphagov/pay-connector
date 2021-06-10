package uk.gov.pay.connector.gatewayaccount.resource;

import org.junit.Test;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GatewayAccountSwitchPaymentProviderRequestValidatorTest {

    @Test
    public void shouldNotThrowWithValidRequest() {
        var request = new GatewayAccountSwitchPaymentProviderRequest("some-user-external-id", "some-cred-id");
        assertDoesNotThrow(() -> GatewayAccountSwitchPaymentProviderRequestValidator.validate(request));
    }

    @Test
    public void shouldThrowWhenUserExternalIdIsNull() {
        var request = new GatewayAccountSwitchPaymentProviderRequest(null, "some-cred-id");
        var thrown = assertThrows(ValidationException.class, () -> GatewayAccountSwitchPaymentProviderRequestValidator.validate(request));
        assertThat(thrown.getErrors().get(0), is("Field [user_external_id] is required"));
    }

    @Test
    public void shouldThrowWhenUserExternalIdIsBlank() {
        var request = new GatewayAccountSwitchPaymentProviderRequest("", "some-cred-id");
        var thrown = assertThrows(ValidationException.class, () -> GatewayAccountSwitchPaymentProviderRequestValidator.validate(request));
        assertThat(thrown.getErrors().get(0), is("Field [user_external_id] is required"));
    }

    @Test
    public void shouldThrowWhenGatewayAccountCredentialIdIsNull() {
        var request = new GatewayAccountSwitchPaymentProviderRequest("some-user-external-id", null);
        var thrown = assertThrows(ValidationException.class, () -> GatewayAccountSwitchPaymentProviderRequestValidator.validate(request));
        assertThat(thrown.getErrors().get(0), is("Field [gateway_account_credential_id] is required"));
    }

    @Test
    public void shouldThrowWhenGatewayAccountCredentialIdIsBlank() {
        var request = new GatewayAccountSwitchPaymentProviderRequest("some-user-external-id", "");
        var thrown = assertThrows(ValidationException.class, () -> GatewayAccountSwitchPaymentProviderRequestValidator.validate(request));
        assertThat(thrown.getErrors().get(0), is("Field [gateway_account_credential_id] is required"));
    }
}
