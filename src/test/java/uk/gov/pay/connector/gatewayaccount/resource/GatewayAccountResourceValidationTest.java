package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.assertj.core.util.Lists;
import org.junit.ClassRule;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountRequest;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GatewayAccountResourceValidationTest {

    private static ConnectorConfiguration mockConnectorConfiguration = mock(ConnectorConfiguration.class);
    
    private static WorldpayConfig mockWorldpayConfig = mock(WorldpayConfig.class);
    
    private static GatewayConfig mockGatewayConfig = mock(GatewayConfig.class);
    
    static {
        when(mockWorldpayConfig.getCredentials()).thenReturn(Lists.emptyList());
        when(mockGatewayConfig.getCredentials()).thenReturn(Lists.emptyList());

        when(mockConnectorConfiguration.getWorldpayConfig()).thenReturn(mockWorldpayConfig);
        when(mockConnectorConfiguration.getSmartpayConfig()).thenReturn(mockGatewayConfig);
        when(mockConnectorConfiguration.getEpdqConfig()).thenReturn(mockGatewayConfig);
    }
    
    @ClassRule
    public static ResourceTestRule resources = ResourceTestRuleWithCustomExceptionMappersBuilder.getBuilder()
            .addResource(new GatewayAccountResource(null, null, null, mockConnectorConfiguration,
                    null, null, null))
            .build();
    
    @Test
    public void shouldReturn400WhenEverythingMissing() {
        Response response = resources.client()
                .target("/v1/api/accounts")
                .request()
                .post(Entity.json(""));

        assertThat(response.getStatus(), is(400));
    }
    
    @Test
    public void shouldReturn400WhenProviderAccountTypeIsInvalid() {

        GatewayAccountRequest input = new GatewayAccountRequest("invalid", null, null, null, null);

        Response response = resources.client()
                .target("/v1/api/accounts")
                .request()
                .post(Entity.json(input));

        assertThat(response.getStatus(), is(400));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("Unsupported payment provider account type, should be one of (test, live)"));
    }
    
    @Test
    public void shouldReturn400WhenPaymentProviderIsInvalid() {
        GatewayAccountRequest input = new GatewayAccountRequest(null, "blockchain", null, null, null);

        Response response = resources.client()
                .target("/v1/api/accounts")
                .request()
                .post(Entity.json(input));

        assertThat(response.getStatus(), is(400));

        String errorMessage = response.readEntity(JsonNode.class).get("message").get(0).textValue();
        assertThat(errorMessage, is("Unsupported payment provider value."));
    }

}
