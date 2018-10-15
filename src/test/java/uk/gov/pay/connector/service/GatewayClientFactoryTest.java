package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import javax.ws.rs.client.Invocation.Builder;
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
    @Mock
    MetricRegistry mockMetricRegistry;
    @Test
    public void shouldBuildGatewayClient() {
        Map<String, String> gatewayUrlMap = mock(Map.class);
        Builder mockBuilder = mock(Builder.class);
        BiFunction<GatewayOrder, Builder, Builder> sessionIdentifier = (GatewayOrder o, Builder b) -> mockBuilder;

        GatewayClient gatewayClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.WORLDPAY, GatewayOperation.AUTHORISE,
                gatewayUrlMap, sessionIdentifier, mockMetricRegistry);

        assertNotNull(gatewayClient);
        verify(mockClientFactory).createWithDropwizardClient(PaymentGatewayName.WORLDPAY, GatewayOperation.AUTHORISE, mockMetricRegistry);
    }
}
