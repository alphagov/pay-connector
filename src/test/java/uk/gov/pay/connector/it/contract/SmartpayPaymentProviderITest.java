package uk.gov.pay.connector.it.contract;

import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.PaymentProviders;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import javax.ws.rs.client.ClientBuilder;

import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;

public class SmartpayPaymentProviderITest {
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        GatewayCredentialsConfig config = app.getConf().getSmartpayConfig();

        PaymentProvider connector = new SmartpayPaymentProvider(
                new GatewayClient(ClientBuilder.newClient(), config.getUrl()),
                gatewayAccountFor(config.getUsername(), config.getPassword()));

        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = connector.authorise(request);

        assertTrue(response.isSuccessful());
    }

    private AuthorisationRequest getCardAuthorisationRequest() {
        Address address = Address.anAddress();
        address.setLine1("41");
        address.setLine2("Scala Street");
        address.setCity("London");
        address.setCounty("London");
        address.setPostcode("EC2A 1AE");
        address.setCountry("GB");

        Card card = aValidSmartpayCard();
        card.setAddress(address);

        String amount = "500";
        String description = "This is the description";
        return new AuthorisationRequest(card, amount, description);
    }

    private Card aValidSmartpayCard() {
        String validSandboxCard = "5555444433331111";
        return buildCardDetails(validSandboxCard, "737", "08/18");
    }
}
