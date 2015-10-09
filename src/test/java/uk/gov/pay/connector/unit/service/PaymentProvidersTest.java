package uk.gov.pay.connector.unit.service;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.app.SmartpayCredentialsConfig;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviders;
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
    public void setup(){
        ConnectorConfiguration config = mock(ConnectorConfiguration.class);
        when(config.getSmartpayConfig()).thenReturn(mock(SmartpayCredentialsConfig.class));
        when(config.getWorldpayConfig()).thenReturn(mock(GatewayCredentialsConfig.class));

        providers = new PaymentProviders(config);
    }

    @Test
    public void shouldResolveSandboxPaymentProvider() throws Exception {
        PaymentProvider sandbox = providers.resolve("sandbox");
        assertThat(sandbox, is(instanceOf(SandboxPaymentProvider.class)));
    }

    @Test
    public void shouldResolveWorldpayPaymentProvider() throws Exception {
        PaymentProvider worldpay = providers.resolve("worldpay");
        assertThat(worldpay, is(instanceOf(WorldpayPaymentProvider.class)));
    }

    @Test
    public void shouldResolveSmartpayPaymentProvider() throws Exception {
        PaymentProvider smartpay = providers.resolve("smartpay");
        assertThat(smartpay, is(instanceOf(SmartpayPaymentProvider.class)));
    }

    @Test
    public void shouldResolveToEmptyForUnknownPaymentProvider() throws Exception {
        String paymentProviderName = "providerNotImplemented";

        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage("Unsupported PaymentProvider " + paymentProviderName);

        providers.resolve(paymentProviderName);
    }
}