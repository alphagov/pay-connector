package uk.gov.pay.connector.it.contract;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.model.AuthorisationRequest;
import uk.gov.pay.connector.model.AuthorisationResponse;
import uk.gov.pay.connector.model.CaptureRequest;
import uk.gov.pay.connector.model.CaptureResponse;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import javax.ws.rs.client.ClientBuilder;

import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;

public class SmartpayPaymentProviderITest {
    public static final String CHARGE_AMOUNT = "500";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();
    private SmartpayPaymentProvider paymentProvider;

    @Before
    public void setup() throws Exception {
        GatewayCredentialsConfig config = app.getConf().getSmartpayConfig();
        GatewayClient gatewayClient = new GatewayClient(ClientBuilder.newClient(), config.getUrl());
        GatewayAccount gatewayAccount = gatewayAccountFor(config.getUsername(), config.getPassword());

        paymentProvider = new SmartpayPaymentProvider(gatewayClient, gatewayAccount);
    }

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        testCardAuthorisation();
    }

    private AuthorisationResponse testCardAuthorisation() {
        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = paymentProvider.authorise(request);
        assertTrue(response.isSuccessful());

        return response;
    }

    @Test
    public void shouldSendSuccessfullyACaptureRequest() throws Exception {
        AuthorisationResponse response = testCardAuthorisation();

        CaptureRequest captureRequest = new CaptureRequest(CHARGE_AMOUNT, response.getTransactionId());
        CaptureResponse captureResponse = paymentProvider.capture(captureRequest);
        assertTrue(captureResponse.isSuccessful());
        assertNull(captureResponse.getError());
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {
        GatewayCredentialsConfig config = app.getConf().getSmartpayConfig();
        GatewayClient gatewayClient = new GatewayClient(ClientBuilder.newClient(), config.getUrl());
        GatewayAccount gatewayAccount = gatewayAccountFor("junk-user", "junk-pass");

        PaymentProvider connector = new SmartpayPaymentProvider(gatewayClient, gatewayAccount);

        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = connector.authorise(request);

        assertFalse(response.isSuccessful());
        assertNotNull(response.getError());
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

        String amount = CHARGE_AMOUNT;
        String description = "This is the description";
        return new AuthorisationRequest(card, amount, description);
    }

    private Card aValidSmartpayCard() {
        String validSandboxCard = "5555444433331111";
        return buildCardDetails(validSandboxCard, "737", "08/18");
    }
}
