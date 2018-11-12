package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.StripePaymentProvider;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.TestClientFactory;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsBuilder.anAuthCardDetails;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

/**
 * This is an integration test with Stripe that should be run manually. In order to make it work you need to set
 * a valid stripe.authToken in the test-it-config.yaml and a valid stripeAccountId (a field in the test).
 * This test will hit the external https://api.stripe.com which is set in in stripe.url in the test-it-config.yaml.
 */
@Ignore
@RunWith(MockitoJUnitRunner.class)
public class StripePaymentProviderTest {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();
    
    @Mock
    private ClientFactory clientFactory;
    
    private StripePaymentProvider stripePaymentProvider;
    
    private String stripeAccountId = "<replace me>";

    @Before
    public void setup() {
        GatewayClientFactory gatewayClientFactory = new GatewayClientFactory(clientFactory);
        when(clientFactory.createWithDropwizardClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any(MetricRegistry.class)))
                .thenReturn(TestClientFactory.createJerseyClient());
        Environment environment = app.getInstanceFromGuiceContainer(Environment.class);
        ConnectorConfiguration connectorConfig = app.getInstanceFromGuiceContainer(ConnectorConfiguration.class);
        stripePaymentProvider = new StripePaymentProvider(gatewayClientFactory, environment, connectorConfig);
    }
    
    @Test
    public void createChargeInStripe() {
        AuthorisationGatewayRequest request = AuthorisationGatewayRequest.valueOf(getCharge(), anAuthCardDetails().withEndDate("01/21").build());
        GatewayResponse gatewayResponse = stripePaymentProvider.authorise(request);
        assertThat(gatewayResponse.isSuccessful()).isTrue();
    }

    private ChargeEntity getCharge() {
        GatewayAccountEntity validGatewayAccount = new GatewayAccountEntity();
        validGatewayAccount.setId(123L);
        validGatewayAccount.setGatewayName(PaymentGatewayName.STRIPE.getName());
        validGatewayAccount.setCredentials(ImmutableMap.of("stripe_account_id", stripeAccountId));
        validGatewayAccount.setType(TEST);
        return aValidChargeEntity()
                .withGatewayAccountEntity(validGatewayAccount)
                .withTransactionId(randomUUID().toString())
                .withDescription("stripe payment provider test charge")
                .build();
    }
}
