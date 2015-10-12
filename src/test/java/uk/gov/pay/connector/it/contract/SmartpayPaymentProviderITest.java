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

import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;

public class SmartpayPaymentProviderITest {
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private static final String CHARGE_AMOUNT = "500";
    private GatewayCredentialsConfig config;

    @Before
    public void setUp(){
         config = app.getConf().getSmartpayConfig();
    }

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider(config.getUsername(), config.getPassword());
        testCardAuthorisation(paymentProvider);
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider("junk-user", "password");
        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = paymentProvider.authorise(request);

        assertFalse(response.isSuccessful());
        assertNotNull(response.getError());
    }

    @Test
    public void shouldSendSuccessfullyACaptureRequest() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider(config.getUsername(), config.getPassword());
        AuthorisationResponse response = testCardAuthorisation(paymentProvider);

        CaptureRequest captureRequest = new CaptureRequest(CHARGE_AMOUNT, response.getTransactionId());
        CaptureResponse captureResponse = paymentProvider.capture(captureRequest);
        assertTrue(captureResponse.isSuccessful());
        assertNull(captureResponse.getError());
    }

    private AuthorisationResponse testCardAuthorisation(PaymentProvider paymentProvider) {
        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = paymentProvider.authorise(request);
        assertTrue(response.isSuccessful());

        return response;
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

    private PaymentProvider getSmartpayPaymentProvider(String username, String password) throws Exception {
        GatewayClient gatewayClient = createGatewayClient(config.getUrl());
        GatewayAccount gatewayAccount = gatewayAccountFor(username, password);
        return new SmartpayPaymentProvider(gatewayClient, gatewayAccount);
    }
}
