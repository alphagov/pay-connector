package uk.gov.pay.connector.gatewayaccount.resource;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gatewayaccount.GatewayAccountSwitchPaymentProviderRequest;
import uk.gov.service.payments.commons.api.exception.ValidationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

 class GatewayAccountSwitchPaymentProviderRequestValidatorTest {

    @Test
     void shouldNotThrowWithValidRequest() {
        var request = new GatewayAccountSwitchPaymentProviderRequest("some-user-external-id", "some-cred-id");
        assertDoesNotThrow(() -> GatewayAccountSwitchPaymentProviderRequestValidator.validate(request));
    }

    @Test
     void shouldThrowWhenUserExternalIdIsNull() {
        var request = new GatewayAccountSwitchPaymentProviderRequest(null, "some-cred-id");
        var thrown = assertThrows(ValidationException.class, () -> GatewayAccountSwitchPaymentProviderRequestValidator.validate(request));
        assertThat(thrown.getErrors().get(0), is("Field [user_external_id] is required"));
    }

    @Test
     void shouldThrowWhenUserExternalIdIsBlank() {
        var request = new GatewayAccountSwitchPaymentProviderRequest("", "some-cred-id");
        var thrown = assertThrows(ValidationException.class, () -> GatewayAccountSwitchPaymentProviderRequestValidator.validate(request));
        assertThat(thrown.getErrors().get(0), is("Field [user_external_id] is required"));
    }

    @Test
     void shouldThrowWhenGatewayAccountCredentialIdIsNull() {
        var request = new GatewayAccountSwitchPaymentProviderRequest("some-user-external-id", null);
        var thrown = assertThrows(ValidationException.class, () -> GatewayAccountSwitchPaymentProviderRequestValidator.validate(request));
        assertThat(thrown.getErrors().get(0), is("Field [gateway_account_credential_external_id] is required"));
    }

    @Test
     void shouldThrowWhenGatewayAccountCredentialIdIsBlank() {
        var request = new GatewayAccountSwitchPaymentProviderRequest("some-user-external-id", "");
        var thrown = assertThrows(ValidationException.class, () -> GatewayAccountSwitchPaymentProviderRequestValidator.validate(request));
        assertThat(thrown.getErrors().get(0), is("Field [gateway_account_credential_external_id] is required"));
    }
}
