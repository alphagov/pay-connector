package uk.gov.pay.connector.gateway.smartpay;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.mockito.Mock;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SMARTPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

public abstract class BaseSmartpayPaymentProviderTest {

    protected SmartpayPaymentProvider provider;

    @Mock
    protected Client mockClient;
    @Mock
    private ClientFactory mockClientFactory;
    @Mock
    private ConnectorConfiguration configuration;
    @Mock
    private GatewayConfig gatewayConfig;
    @Mock
    private Environment environment;
    @Mock
    private MetricRegistry metricRegistry;

    @Before
    public void setup() {
        GatewayClientFactory gatewayClientFactory = new GatewayClientFactory(mockClientFactory);

        when(mockClientFactory.createWithDropwizardClient(eq(PaymentGatewayName.SMARTPAY), any(MetricRegistry.class)))
                .thenReturn(mockClient);
        when(configuration.getGatewayConfigFor(PaymentGatewayName.SMARTPAY)).thenReturn(gatewayConfig);
        when(gatewayConfig.getUrls()).thenReturn(ImmutableMap.of(TEST.toString(), "http://smartpay.url"));
        when(environment.metrics()).thenReturn(metricRegistry);
        when(metricRegistry.histogram(anyString())).thenReturn(mock(Histogram.class));

        provider = new SmartpayPaymentProvider(configuration, gatewayClientFactory, environment);
    }

    GatewayAccountEntity aServiceAccount() {
        GatewayAccountEntity gatewayAccount = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName(SMARTPAY.getName())
                .withType(TEST)
                .build();
        GatewayAccountCredentialsEntity creds = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of(
                        "username", "theUsername",
                        "password", "thePassword",
                        "merchant_id", "theMerchantCode"))
                .withGatewayAccountEntity(gatewayAccount)
                .withPaymentProvider(SMARTPAY.getName())
                .withState(ACTIVE)
                .build();

        gatewayAccount.setGatewayAccountCredentials(List.of(creds));

        return gatewayAccount;
    }

    void mockSmartpayResponse(int httpStatus, String responsePayload) {
        WebTarget mockTarget = mock(WebTarget.class);
        when(mockClient.target(any(URI.class))).thenReturn(mockTarget);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        when(mockTarget.request()).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), any(Object.class))).thenReturn(mockBuilder);

        Response response = mock(Response.class);
        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(mockBuilder.post(any(Entity.class))).thenReturn(response);

        when(response.getStatus()).thenReturn(httpStatus);
    }
}
