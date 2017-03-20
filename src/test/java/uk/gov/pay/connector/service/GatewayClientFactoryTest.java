package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MediaType;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class GatewayClientFactoryTest {

    @InjectMocks
    GatewayClientFactory gatewayClientFactory;

    @Mock
    ClientFactory mockClientFactory;

    @Test
    public void shouldBuildGatewayClient() {
        Map<String, String> gatewayUrlMap = mock(Map.class);
        Builder mockBuilder = mock(Builder.class);
        BiFunction<GatewayOrder, Builder, Builder> sessionIdentifier = (GatewayOrder o, Builder b) -> mockBuilder;
        MetricRegistry mockMetricRegistry = mock(MetricRegistry.class);

        GatewayClient gatewayClient = gatewayClientFactory.createGatewayClient(SupportedPaymentGateway.WORLDPAY, GatewayOperation.AUTHORISE,
                gatewayUrlMap, MediaType.TEXT_XML_TYPE, sessionIdentifier, mockMetricRegistry);

        assertNotNull(gatewayClient);
        verify(mockClientFactory).createWithDropwizardClient(SupportedPaymentGateway.WORLDPAY, GatewayOperation.AUTHORISE);
    }
}
