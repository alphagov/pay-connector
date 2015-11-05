package uk.gov.pay.connector.it.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.util.AuthorisationUtils;
import uk.gov.pay.connector.util.JerseyClientFactory;

import javax.ws.rs.client.Client;
import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.*;
import static uk.gov.pay.connector.model.CancelRequest.cancelRequest;
import static uk.gov.pay.connector.model.domain.GatewayAccount.gatewayAccountFor;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.util.AuthorisationUtils.CHARGE_AMOUNT;
import static uk.gov.pay.connector.util.AuthorisationUtils.getCardAuthorisationRequest;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

public class SmartpayPaymentProviderTest {
    private String url = "https://pal-test.barclaycardsmartpay.com/pal/servlet/soap/Payment";
    private String username = envOrThrow("GDS_CONNECTOR_SMARTPAY_USER");
    private String password = envOrThrow("GDS_CONNECTOR_SMARTPAY_PASSWORD");

    @Before
    public void setUpAndCheckThatSmartpayIsUp() {
        try {
            new URL(url).openConnection().connect();
        } catch (IOException ex) {
            Assume.assumeTrue(false);
        }
    }

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider(username, password);
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
    public void shouldSuccessfullySendACaptureRequest() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider(username, password);
        AuthorisationResponse response = testCardAuthorisation(paymentProvider);

        CaptureRequest captureRequest = new CaptureRequest(CHARGE_AMOUNT, response.getTransactionId());
        CaptureResponse captureResponse = paymentProvider.capture(captureRequest);
        assertTrue(captureResponse.isSuccessful());
        assertNull(captureResponse.getError());
    }

    @Test
    public void shouldSuccessfullySendACancelRequest() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider(username, password);
        AuthorisationResponse response = testCardAuthorisation(paymentProvider);

        CancelResponse cancelResponse = paymentProvider.cancel(cancelRequest(response.getTransactionId()));
        assertTrue(cancelResponse.isSuccessful());
        assertNull(cancelResponse.getError());

    }

    private AuthorisationResponse testCardAuthorisation(PaymentProvider paymentProvider) {
        AuthorisationRequest request = getCardAuthorisationRequest();
        AuthorisationResponse response = paymentProvider.authorise(request);
        assertTrue(response.isSuccessful());

        return response;
    }

    private PaymentProvider getSmartpayPaymentProvider(String username, String password) throws Exception {
        Client client = JerseyClientFactory.createJerseyClient();
        GatewayClient gatewayClient = createGatewayClient(client, url);
        GatewayAccount gatewayAccount = gatewayAccountFor(username, password);
        return new SmartpayPaymentProvider(gatewayClient, gatewayAccount, new ObjectMapper());
    }
}
