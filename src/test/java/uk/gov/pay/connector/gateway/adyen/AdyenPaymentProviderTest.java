package uk.gov.pay.connector.gateway.adyen;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.core.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.refund.service.RefundEntityFactory;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;

@ExtendWith(MockitoExtension.class)
class AdyenPaymentProviderTest {

    private AdyenPaymentProvider provider;
    @Mock
    private ConnectorConfiguration configuration;
    @Mock
    private GatewayClientFactory gatewayClientFactory;
    @Mock
    private GatewayClient gatewayClient;
    @Mock
    private Environment environment;
    @Mock
    private RefundEntityFactory refundEntityFactory;
    @Mock
    private AdyenGatewayConfig adyenGatewayConfig;
    @Mock
    private MetricRegistry metricRegistry;
    @Mock
    JsonObjectMapper jsonObjectMapper;

    @BeforeEach
    void setUp() {
        when(configuration.getAdyenGatewayConfig()).thenReturn(adyenGatewayConfig);
        when(gatewayClientFactory.createGatewayClient(eq(ADYEN), any(MetricRegistry.class))).thenReturn(gatewayClient);
        when(environment.metrics()).thenReturn(metricRegistry);

        this.provider = new AdyenPaymentProvider(configuration, environment, gatewayClientFactory, refundEntityFactory, jsonObjectMapper);
    }

    @Test
    void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("adyen"));
    }

    @Test
    void shouldReturnCanQueryStatusAsFalse() {
        assertThat(provider.canQueryPaymentStatus(), is(false));
    }

    @Test
    void shouldGenerateEmptyTransactionId() {
        assertThat(provider.generateTransactionId().isEmpty(), is(true));
    }
}
