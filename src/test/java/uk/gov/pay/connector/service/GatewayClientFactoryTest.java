package uk.gov.pay.connector.service;

import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;

@ExtendWith(MockitoExtension.class)
class GatewayClientFactoryTest {

    @InjectMocks
    GatewayClientFactory gatewayClientFactory;

    @Mock
    ClientFactory mockClientFactory;
    @Mock
    MetricRegistry mockMetricRegistry;
    @Test
    void shouldBuildGatewayClient() {
        GatewayClient gatewayClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.WORLDPAY, AUTHORISE, mockMetricRegistry);

        assertNotNull(gatewayClient);
        verify(mockClientFactory).createWithDropwizardClient(PaymentGatewayName.WORLDPAY, AUTHORISE, mockMetricRegistry);
    }
}
