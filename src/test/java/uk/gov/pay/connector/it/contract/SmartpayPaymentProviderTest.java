package uk.gov.pay.connector.it.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.GatewayAccount;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.PaymentProvider;
import uk.gov.pay.connector.service.smartpay.SmartpayPaymentProvider;
import uk.gov.pay.connector.util.JerseyClientFactory;

import javax.ws.rs.client.Client;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.function.Consumer;

import static com.google.common.io.Resources.getResource;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.model.CancelRequest.cancelRequest;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.util.AuthorisationUtils.CHARGE_AMOUNT;
import static uk.gov.pay.connector.util.AuthorisationUtils.getCardAuthorisationRequest;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

public class SmartpayPaymentProviderTest {
    private String url = "https://pal-test.barclaycardsmartpay.com/pal/servlet/soap/Payment";
    private String username = envOrThrow("GDS_CONNECTOR_SMARTPAY_USER");
    private String password = envOrThrow("GDS_CONNECTOR_SMARTPAY_PASSWORD");
    private GatewayAccount validGatewayAccount;

    @Before
    public void setUpAndCheckThatSmartpayIsUp() {
        try {
            new URL(url).openConnection().connect();
            Map<String, String> validSmartPayCredentials = ImmutableMap.of(
                    "username", username,
                    "password", password);
            validGatewayAccount = new GatewayAccount(123L, "smartpay", validSmartPayCredentials);
        } catch (IOException ex) {
            Assume.assumeTrue(false);
        }
    }

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider();
        testCardAuthorisation(paymentProvider);
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider();
        GatewayAccount accountWithInvalidCredentials = new GatewayAccount(11L, "smartpay", ImmutableMap.of(
                "username","wrong-username",
                "password","wrong-password"
        ));
        AuthorisationRequest request = getCardAuthorisationRequest(accountWithInvalidCredentials);
        AuthorisationResponse response = paymentProvider.authorise(request);

        assertFalse(response.isSuccessful());
        assertNotNull(response.getError());
    }

    @Test
    public void shouldSuccessfullySendACaptureRequest() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider();
        AuthorisationResponse response = testCardAuthorisation(paymentProvider);

        CaptureRequest captureRequest = new CaptureRequest(CHARGE_AMOUNT, response.getTransactionId(), validGatewayAccount);
        CaptureResponse captureResponse = paymentProvider.capture(captureRequest);
        assertTrue(captureResponse.isSuccessful());
        assertNull(captureResponse.getError());
    }

    @Test
    public void shouldSuccessfullySendACancelRequest() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider();
        AuthorisationResponse response = testCardAuthorisation(paymentProvider);

        CancelResponse cancelResponse = paymentProvider.cancel(cancelRequest(response.getTransactionId(), validGatewayAccount));
        assertTrue(cancelResponse.isSuccessful());
        assertNull(cancelResponse.getError());

    }

    @Test
    public void shouldBeAbleToHandleNotification() throws Exception {
        PaymentProvider paymentProvider = getSmartpayPaymentProvider();
        AuthorisationResponse response = testCardAuthorisation(paymentProvider);

        Consumer<StatusUpdates> accountUpdater = mock(Consumer.class);

        String transactionId = response.getTransactionId();
        StatusUpdates statusResponse = paymentProvider.handleNotification(
                notificationPayloadForTransaction(transactionId),
                x -> validGatewayAccount,
                accountUpdater
        );

        assertThat(statusResponse.getStatusUpdates(), hasItem(Pair.of(transactionId, CAPTURED)));
    }

    private AuthorisationResponse testCardAuthorisation(PaymentProvider paymentProvider) {
        AuthorisationRequest request = getCardAuthorisationRequest(validGatewayAccount);
        AuthorisationResponse response = paymentProvider.authorise(request);
        assertTrue(response.isSuccessful());

        return response;
    }

    private PaymentProvider getSmartpayPaymentProvider() throws Exception {
        Client client = JerseyClientFactory.createJerseyClient();
        GatewayClient gatewayClient = createGatewayClient(client, url);
        return new SmartpayPaymentProvider(gatewayClient, new ObjectMapper());
    }

    private String notificationPayloadForTransaction(String transactionId) throws IOException {
        URL resource = getResource("templates/smartpay/notification-capture.json");
        return Resources.toString(resource, Charset.defaultCharset()).replace("{{transactionId}}", transactionId);
    }
}
