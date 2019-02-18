package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;

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
        GatewayClient gatewayClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.WORLDPAY, AUTHORISE, emptyMap(), mockMetricRegistry);

        assertNotNull(gatewayClient);
        verify(mockClientFactory).createWithDropwizardClient(PaymentGatewayName.WORLDPAY, AUTHORISE, mockMetricRegistry);
    }
}
