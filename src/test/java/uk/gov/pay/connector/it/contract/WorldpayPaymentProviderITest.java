package uk.gov.pay.connector.it.contract;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import javax.ws.rs.client.ClientBuilder;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.util.CardUtils.aValidCard;

public class WorldpayPaymentProviderITest {
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Before
    public void before() throws Exception {
        Assume.assumeTrue(worldPayEnvironmentInitialized());
    }

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        WorldpayConfig config = getWorldpayConfig();
        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(
                new GatewayClient(
                        ClientBuilder.newClient(),
                        config.getUrl()
                ),
                gatewayAccountFor(config.getUsername(), config.getPassword())
        );
        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = connector.authorise(request);

        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {
        String worldpayUrl = getWorldpayConfig().getUrl();

        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(
                new GatewayClient(ClientBuilder.newClient(), worldpayUrl),
                gatewayAccountFor("wrongUsername", "wrongPassword"));

        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = connector.authorise(request);

        assertFalse(response.isSuccessful());
    }

    private AuthorisationRequest getCardAuthorisationRequest() {
        Card card = aValidCard();
        String amount = "500";
        String description = "This is the description";
        return new AuthorisationRequest(card, amount, description);
    }

    private boolean worldPayEnvironmentInitialized() {
        WorldpayConfig worldpayConfig = getWorldpayConfig();
        return isNotBlank(worldpayConfig.getUsername()) && isNotBlank(worldpayConfig.getPassword());
    }

    private WorldpayConfig getWorldpayConfig() {
        return app.getConf().getWorldpayConfig();
    }
}
