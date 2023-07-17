package uk.gov.pay.connector.gateway.stripe;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import uk.gov.pay.connector.gateway.ChargeQueryGatewayRequest;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_STRIPE_ACCOUNT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

public class StripePaymentProviderIT {

    @ClassRule
    public static DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(app.getWireMockPort());
    
    private StripePaymentProvider stripePaymentProvider;
    private StripeMockClient stripeMockClient;
    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity;

    @Before
    public void setUp() throws Exception {
        stripePaymentProvider = app.getInstanceFromGuiceContainer(StripePaymentProvider.class);
        stripeMockClient = new StripeMockClient(wireMockRule);
        gatewayAccountCredentialsEntity = GatewayAccountCredentialsEntityFixture
                .aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of(CREDENTIALS_STRIPE_ACCOUNT_ID, "account_123"))
                .build();
        gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity))
                .build();
    }

    @Test
    public void shouldBuildStripeQueryRequestWithMetadata() throws GatewayException {
        String chargeExternalId = "a-charge-external-id";
        stripeMockClient.mockSearchPaymentIntentsByMetadata(chargeExternalId);

        ChargeQueryGatewayRequest request = new ChargeQueryGatewayRequest(gatewayAccountEntity, 
                gatewayAccountCredentialsEntity, chargeExternalId, chargeExternalId, AuthorisationMode.WEB, false);
        ChargeQueryResponse chargeQueryResponse = stripePaymentProvider.queryPaymentStatus(request);
        
        assertThat(chargeQueryResponse.foundCharge(), is(true));
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.of(CAPTURED)));
    }
}
