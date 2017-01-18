package uk.gov.pay.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.app.WorldpayNotificationConfig;
import uk.gov.pay.connector.resources.PaymentGatewayName;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PaymentProvidersTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private PaymentProviders providers;

    @Before
    public void setup() {
        ConnectorConfiguration config = mock(ConnectorConfiguration.class);
        ClientFactory client = mock(ClientFactory.class);
        Environment environment = mock(Environment.class);
        when(config.getSmartpayConfig()).thenReturn(mock(GatewayCredentialsConfig.class));
        when(config.getWorldpayConfig()).thenReturn(mock(WorldpayNotificationConfig.class));

        providers = new PaymentProviders(config, client, new ObjectMapper(), environment);
    }

    @Test
    public void shouldResolveSandboxPaymentProvider() throws Exception {
        PaymentProvider sandbox = providers.byName(PaymentGatewayName.SANDBOX);
        assertThat(sandbox, is(instanceOf(SandboxPaymentProvider.class)));
    }

    @Test
    public void shouldResolveWorldpayPaymentProvider() throws Exception {
        PaymentProvider worldpay = providers.byName(PaymentGatewayName.WORLDPAY);
        assertThat(worldpay, is(instanceOf(WorldpayPaymentProvider.class)));
    }

    @Test
    public void shouldResolveSmartpayPaymentProvider() throws Exception {
        PaymentProvider smartpay = providers.byName(PaymentGatewayName.SMARTPAY);
        assertThat(smartpay, is(instanceOf(SmartpayPaymentProvider.class)));
    }
}
