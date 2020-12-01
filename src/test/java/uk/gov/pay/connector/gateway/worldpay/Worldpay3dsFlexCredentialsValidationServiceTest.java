package uk.gov.pay.connector.gateway.worldpay;

import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.charge.service.Worldpay3dsFlexJwtService;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccount;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentials;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

@ExtendWith(MockitoExtension.class)
class Worldpay3dsFlexCredentialsValidationServiceTest {

    private static final String DDC_TOKEN = "aDdcToken";
    
    private Worldpay3dsFlexCredentialsValidationService service;
    
    private Map<String, String> threeDsFlexDdcUrls = Map.of(
            "test", "https://a/test/url", 
            "live", "https://a/live/url");

    @Mock
    private ClientFactory clientFactory;
    
    @Mock
    private Environment environment;
    
    @Mock
    private Worldpay3dsFlexJwtService worldpay3dsFlexJwtService;
    
    @Mock
    private ConnectorConfiguration connectorConfiguration;
    
    @Mock
    private Invocation.Builder invocationBuilder;
    
    @Mock
    private Response response;
    
    @Mock
    private Client client;
    
    @Mock
    private WebTarget webTarget;

    @BeforeEach
    void setup() {
        mockConnectorConfiguration();
        mockJerseyClient();
        service = new Worldpay3dsFlexCredentialsValidationService(clientFactory, environment, 
                worldpay3dsFlexJwtService, connectorConfiguration);
    }

    @ParameterizedTest
    @ValueSource(strings = { "test", "live" })
    void account_credentials_are_valid_for_a_gateway_account(String type) {
        var gatewayAccount = aGatewayAccountEntity().withType(GatewayAccountEntity.Type.fromString(type)).build();
        var flexCredentials = new Worldpay3dsFlexCredentials("53f0917f101a4428b69d5fb0",
                "57992a087a0c4849895ab8a2", "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9");

        when(response.getStatus()).thenReturn(HttpStatus.SC_OK);
        when(client.target(threeDsFlexDdcUrls.get(type))).thenReturn(webTarget);
        when(worldpay3dsFlexJwtService.generateDdcToken(eq(GatewayAccount.valueOf(gatewayAccount)), eq(flexCredentials), 
                any(ZonedDateTime.class))).thenReturn(DDC_TOKEN);
        var expectedFormData = new MultivaluedHashMap<String, String>(){{add("JWT", DDC_TOKEN);}};
        when(invocationBuilder.post(argThat(new EntityMatcher(Entity.form(expectedFormData))))).thenReturn(response);
        
        assertTrue(service.validateCredentials(gatewayAccount, flexCredentials));
    }

    @Test
    @Disabled
    void account_credentials_are_invalid_for_a_live_gateway_account() {
        //TODO implement
    }

    @Test
    @Disabled
    void gateway_account_is_not_a_worldpay_account() {
        //TODO implement
    }

    private void mockJerseyClient() {
        MetricRegistry metricRegistry = mock(MetricRegistry.class);
        when(environment.metrics()).thenReturn(metricRegistry);
        when(clientFactory.createWithDropwizardClient(any(PaymentGatewayName.class), any(MetricRegistry.class)))
                .thenReturn(client);
        when(webTarget.request()).thenReturn(invocationBuilder);
    }
    
    private void mockConnectorConfiguration() {
        WorldpayConfig worldpayConfig = mock(WorldpayConfig.class);
        when(connectorConfiguration.getWorldpayConfig()).thenReturn(worldpayConfig);
        when(worldpayConfig.getThreeDsFlexDdcUrls()).thenReturn(threeDsFlexDdcUrls);
    }

}
