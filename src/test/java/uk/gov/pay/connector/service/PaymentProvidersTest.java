package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.gateway.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;

import java.util.Map;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.GatewayOperation.CANCEL;
import static uk.gov.pay.connector.gateway.GatewayOperation.CAPTURE;
import static uk.gov.pay.connector.gateway.GatewayOperation.REFUND;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;

@RunWith(MockitoJUnitRunner.class)
public class PaymentProvidersTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private PaymentProviders providers;

    @Mock
    GatewayClientFactory gatewayClientFactory;

    @Mock
    ConnectorConfiguration config;

    @Mock
    GatewayConfig smartpayConfig;

    @Mock
    Map<String, String> smartpayUrlMap;
    
    @Mock
    MetricRegistry metricRegistry;

    @Before
    public void setup() {
        Environment environment = mock(Environment.class);
        when(config.getGatewayConfigFor(PaymentGatewayName.SMARTPAY)).thenReturn(smartpayConfig);
        when(smartpayConfig.getUrls()).thenReturn(smartpayUrlMap);
        when(environment.metrics()).thenReturn(metricRegistry);

        providers = new PaymentProviders(config, gatewayClientFactory, new ObjectMapper(), environment, mock(WorldpayPaymentProvider.class), mock(EpdqPaymentProvider.class));
    }

    @Test
    public void shouldResolveSandboxPaymentProvider() {
        PaymentProvider sandbox = providers.byName(PaymentGatewayName.SANDBOX);
        assertThat(sandbox, is(instanceOf(SandboxPaymentProvider.class)));
    }

    @Test
    public void shouldResolveWorldpayPaymentProvider() {
        PaymentProvider worldpay = providers.byName(PaymentGatewayName.WORLDPAY);
        assertThat(worldpay, is(instanceOf(WorldpayPaymentProvider.class)));
    }

    @Test
    public void shouldResolveSmartpayPaymentProvider() {
        PaymentProvider smartpay = providers.byName(PaymentGatewayName.SMARTPAY);
        assertThat(smartpay, is(instanceOf(SmartpayPaymentProvider.class)));
    }

    @Test
    public void shouldResolveEpdqPaymentProvider() {
        PaymentProvider epdq = providers.byName(EPDQ);
        assertThat(epdq, is(instanceOf(EpdqPaymentProvider.class)));
    }

    @Test
    public void shouldSetupGatewayClientForGatewayOperations() {
        verify(gatewayClientFactory).createGatewayClient(SMARTPAY, AUTHORISE, smartpayUrlMap,
            SmartpayPaymentProvider.includeSessionIdentifier(), metricRegistry);
        verify(gatewayClientFactory).createGatewayClient(SMARTPAY, CANCEL, smartpayUrlMap,
            SmartpayPaymentProvider.includeSessionIdentifier(), metricRegistry);
        verify(gatewayClientFactory).createGatewayClient(SMARTPAY, CAPTURE, smartpayUrlMap,
            SmartpayPaymentProvider.includeSessionIdentifier(), metricRegistry);
        verify(gatewayClientFactory).createGatewayClient(SMARTPAY, REFUND, smartpayUrlMap,
            SmartpayPaymentProvider.includeSessionIdentifier(), metricRegistry);

    }
}
