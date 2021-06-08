package uk.gov.pay.connector.gatewayaccount.resource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.common.exception.ValidationException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountCredentialsRequest;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GatewayAccountCredentialsRequestValidatorTest {
    @Mock
    ConnectorConfiguration connectorConfiguration;
    @Mock
    WorldpayConfig worldpayConfig;
    @Mock
    StripeGatewayConfig stripeGatewayConfig;
    @Mock
    GatewayConfig gatewayConfig;

    GatewayAccountCredentialsRequestValidator gatewayAccountCredentialsRequestValidator;

    @Before
    public void before() {
        when(worldpayConfig.getCredentials()).thenReturn(List.of("merchant_id"));
        when(gatewayConfig.getCredentials()).thenReturn(List.of());
        when(stripeGatewayConfig.getCredentials()).thenReturn(List.of());
        when(connectorConfiguration.getWorldpayConfig()).thenReturn(worldpayConfig);
        when(connectorConfiguration.getSmartpayConfig()).thenReturn(gatewayConfig);
        when(connectorConfiguration.getEpdqConfig()).thenReturn(gatewayConfig);
        when(connectorConfiguration.getStripeConfig()).thenReturn(stripeGatewayConfig);

        gatewayAccountCredentialsRequestValidator = new GatewayAccountCredentialsRequestValidator(connectorConfiguration);
    }

    @Test
    public void shouldNotThrowWithValidRequest() {
        var request = new GatewayAccountCredentialsRequest("worldpay", Map.of("merchant_id", "some-merchant-id"));
        assertDoesNotThrow(() -> gatewayAccountCredentialsRequestValidator.validateCreate(request));
    }

    @Test
    public void shouldThrowWhenPaymentProviderIsMissing() {
        var request = new GatewayAccountCredentialsRequest(null, null);
        var thrown = assertThrows(ValidationException.class, () -> gatewayAccountCredentialsRequestValidator.validateCreate(request));
        assertThat(thrown.getErrors().get(0), is("Field(s) missing: [payment_provider]"));
    }

    @Test
    public void shouldThrowWhenPaymentProviderIsNotStripeOrWorldpay() {
        var request = new GatewayAccountCredentialsRequest("smartpay", null);
        var thrown = assertThrows(ValidationException.class, () -> gatewayAccountCredentialsRequestValidator.validateCreate(request));
        assertThat(thrown.getErrors().get(0), is("Operation not supported for payment provider 'smartpay'"));
    }

    @Test
    public void shouldThrowWhenCredentialsAreMissing() {
        var request = new GatewayAccountCredentialsRequest("worldpay", Map.of("missing_merchant_id", "some-merchant-id"));
        var thrown = assertThrows(ValidationException.class, () -> gatewayAccountCredentialsRequestValidator.validateCreate(request));
        assertThat(thrown.getErrors().get(0), is("Field(s) missing: [merchant_id]"));
    }
}
