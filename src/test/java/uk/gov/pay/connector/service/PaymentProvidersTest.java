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
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.gateway.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;

import javax.ws.rs.client.Invocation.Builder;
import java.util.Map;
import java.util.function.BiFunction;

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
    LinksConfig linksConfig;

    @Mock
    WorldpayConfig worldpayConfig;

    @Mock
    GatewayConfig smartpayConfig;

    @Mock
    GatewayConfig epdqConfig;

    @Mock
    Map<String, String> worldpayUrlMap;

    @Mock
    Map<String, String> smartpayUrlMap;

    @Mock
    Map<String, String> epdqUrlMap;

    @Mock
    BiFunction<GatewayOrder, Builder, Builder> sessionIdentifier;

    @Mock
    MetricRegistry metricRegistry;

    @Before
    public void setup() {
        Environment environment = mock(Environment.class);
        when(config.getGatewayConfigFor(PaymentGatewayName.SMARTPAY)).thenReturn(smartpayConfig);
        when(config.getGatewayConfigFor(EPDQ)).thenReturn(epdqConfig);
        when(config.getLinks()).thenReturn(linksConfig);
        when(linksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");
        when(smartpayConfig.getUrls()).thenReturn(smartpayUrlMap);
        when(epdqConfig.getUrls()).thenReturn(epdqUrlMap);
        when(environment.metrics()).thenReturn(metricRegistry);

        providers = new PaymentProviders(config, gatewayClientFactory, new ObjectMapper(), environment, mock(WorldpayPaymentProvider.class));
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

    @Test
    public void shouldResolveEpdqPaymentProvider() throws Exception {
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

        verify(gatewayClientFactory).createGatewayClient(EPDQ, AUTHORISE, epdqUrlMap,
            EpdqPaymentProvider.includeSessionIdentifier(), metricRegistry);
        verify(gatewayClientFactory).createGatewayClient(EPDQ, CANCEL, epdqUrlMap,
            EpdqPaymentProvider.includeSessionIdentifier(), metricRegistry);
        verify(gatewayClientFactory).createGatewayClient(EPDQ, CAPTURE, epdqUrlMap,
            EpdqPaymentProvider.includeSessionIdentifier(), metricRegistry);
        verify(gatewayClientFactory).createGatewayClient(EPDQ, REFUND, epdqUrlMap,
            EpdqPaymentProvider.includeSessionIdentifier(), metricRegistry);

    }
}
