package uk.gov.pay.connector.gateway.stripe;

import io.dropwizard.testing.ConfigOverride;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.rules.GuiceAppRule;

import javax.ws.rs.core.Response;

import java.util.Optional;

import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// README - make sure you spin up a very simple http server locally on port 8181 that accepts any POST requests
// Example code in Python : https://gist.github.com/bradmontgomery/2219997
// TODO - Use the Postgres rule to start the database as well
public class StripeGatewayClientLoadTest {
    
    private CardAuthorisationGatewayRequest request = mock(CardAuthorisationGatewayRequest.class);
    
    private GatewayAccountEntity gatewayAccount = mock(GatewayAccountEntity.class);
    
    private AuthCardDetails authCardDetails = mock(AuthCardDetails.class);
    
    @Rule
    public GuiceAppRule<ConnectorConfiguration> guiceAppRule = new GuiceAppRule<>(ConnectorApp.class, 
            resourceFilePath("config/test-it-config.yaml"), 
            ConfigOverride.config("stripe.url", "http://localhost:8181"));
    
    @Test
    public void shouldFallOverWhenWeHitStripeHard() throws GatewayClientException, GatewayException, DownstreamException {

        when(request.getGatewayAccount()).thenReturn(gatewayAccount);
        when(gatewayAccount.getGatewayName()).thenReturn("a-gateway-account");
        when(gatewayAccount.getType()).thenReturn("a-type");

        when(request.getAuthCardDetails()).thenReturn(authCardDetails);
        when(authCardDetails.getAddress()).thenReturn(Optional.empty());
        when(authCardDetails.getCardNo()).thenReturn("a-card-number");
        when(authCardDetails.expiryMonth()).thenReturn("a-month");
        when(authCardDetails.expiryYear()).thenReturn("a-year");
        when(authCardDetails.getCardHolder()).thenReturn("a-card-holder");

        StripePaymentProvider stripePaymentProvider = guiceAppRule.getInstanceFromGuiceContainer(StripePaymentProvider.class);
        
        int count = 0;
        
        while (++count <= 2000) {

            System.out.println("Test count is " + count);
            
            Response response = stripePaymentProvider.createToken(request);

            assertThat(response.getStatus(), is(200));
        }
    }
}
