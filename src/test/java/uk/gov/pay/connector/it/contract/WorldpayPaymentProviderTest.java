package uk.gov.pay.connector.it.contract;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.StatusUpdates;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayCaptureResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;

import javax.ws.rs.client.ClientBuilder;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.io.Resources.getResource;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.util.CardUtils.aValidCard;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

public class WorldpayPaymentProviderTest {

    private GatewayAccountEntity validGatewayAccount;
    private Map<String, String> validCredentials;
    private ChargeEntity chargeEntity;
    @Before
    public void checkThatWorldpayIsUp() {
        try {
            new URL(getWorldpayConfig().getUrls().get(TEST.toString())).openConnection().connect();

            validCredentials = ImmutableMap.of(
                    "merchant_id", "MERCHANTCODE",
                    "username", envOrThrow("GDS_CONNECTOR_WORLDPAY_USER"),
                    "password", envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD"));

            validGatewayAccount = new GatewayAccountEntity();
            validGatewayAccount.setId(1234L);
            validGatewayAccount.setGatewayName("worldpay");
            validGatewayAccount.setCredentials(validCredentials);
            validGatewayAccount.setType(TEST);

            chargeEntity = aValidChargeEntity()
                    .withTransactionId(randomUUID().toString())
                    .withGatewayAccountEntity(validGatewayAccount)
                    .build();
        } catch (IOException ex) {
            Assume.assumeTrue(false);
        }
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchant() throws Exception {
        WorldpayPaymentProvider connector = getValidWorldpayPaymentProvider();
        successfulWorldpayCardAuth(connector);
    }

    /**
     * Worldpay does not care about a successful authorization reference to make a capture request.
     * It simply accepts anything as long as the request is well formed. (And ignores it silently)
     */
    @Test
    public void shouldBeAbleToSendCaptureRequestForMerchant() throws Exception {
        WorldpayPaymentProvider connector = getValidWorldpayPaymentProvider();
        GatewayResponse response = connector.capture(CaptureGatewayRequest.valueOf(chargeEntity));

        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldBeAbleToSubmitAPartialRefundAfterACaptureHasBeenSubmitted() throws InterruptedException {

        WorldpayPaymentProvider connector = getValidWorldpayPaymentProvider();

        GatewayResponse<WorldpayOrderStatusResponse> response = successfulWorldpayCardAuth(connector);

        assertThat(response.getBaseResponse().isPresent(), is(true));
        String transactionId = response.getBaseResponse().get().getTransactionId();

        assertThat(response.isSuccessful(), is(true));
        assertThat(transactionId, is(not(nullValue())));

        chargeEntity.setGatewayTransactionId(transactionId);

        GatewayResponse<WorldpayCaptureResponse> captureResponse = connector.capture(CaptureGatewayRequest.valueOf(chargeEntity));

        assertThat(captureResponse.isSuccessful(), is(true));

        GatewayResponse refundGatewayResponse = connector.refund(RefundGatewayRequest.valueOf(chargeEntity));

        assertTrue(refundGatewayResponse.isSuccessful());
    }

    @Test
    public void shouldBeAbleToSendCancelRequestForMerchant() throws Exception {
        WorldpayPaymentProvider connector = getValidWorldpayPaymentProvider();
        GatewayResponse<WorldpayOrderStatusResponse> response = successfulWorldpayCardAuth(connector);

        assertThat(response.getBaseResponse().isPresent(), is(true));
        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));

        chargeEntity.setGatewayTransactionId(transactionId);

        CancelGatewayRequest cancelGatewayRequest = CancelGatewayRequest.valueOf(chargeEntity);
        GatewayResponse cancelResponse = connector.cancel(cancelGatewayRequest);

        assertThat(cancelResponse.isSuccessful(), is(true));
    }

   /* @Test
    public void handleNotification_shouldEnquiryToVerifyTheStatus() throws Exception {
        WorldpayPaymentProvider connector = getValidWorldpayPaymentProvider();
        GatewayResponse<WorldpayOrderStatusResponse> response = successfulWorldpayCardAuth(connector);

        Consumer<StatusUpdates> mockAccountUpdater = mock(Consumer.class);

        assertThat(response.getBaseResponse().isPresent(), is(true));
        String transactionId = response.getBaseResponse().get().getTransactionId();

        chargeEntity.setGatewayTransactionId(transactionId);

        GatewayResponse<WorldpayCaptureResponse> captureResponse = connector.capture(CaptureGatewayRequest.valueOf(chargeEntity));

        assertThat(captureResponse.isSuccessful(), is(true));

        chargeEntity.setGatewayTransactionId(transactionId);
        assertThat(transactionId, is(not(nullValue())));

        StatusUpdates statusResponse = connector.handleNotification(
                notificationPayloadForTransaction(transactionId, "CAPTURED"),
                payloadChecks -> true,
                accoundFinder -> Optional.of(chargeEntity),
                mockAccountUpdater
        );

        assertThat(statusResponse.successful(), is(true));
        assertThat(statusResponse.getStatusUpdates(), is(empty()));
        verifyZeroInteractions(mockAccountUpdater);
    }*/

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {

        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(
                createGatewayClient(ClientBuilder.newClient(), getWorldpayConfig().getUrls())
        );

        Long gatewayAccountId = 112233L;
        String providerName = "worldpay";
        ImmutableMap<String, String> credentials = ImmutableMap.of(
                "merchant_id", "non-existent-id",
                "username", "non-existent-username",
                "password", "non-existent-password"
        );

        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(providerName, credentials, TEST);
        gatewayAccountEntity.setId(gatewayAccountId);

        ChargeEntity charge = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();

        AuthorisationGatewayRequest request = new AuthorisationGatewayRequest(charge, aValidCard());
        GatewayResponse<WorldpayOrderStatusResponse> response = connector.authorise(request);

        assertFalse(response.isSuccessful());
    }

    private AuthorisationGatewayRequest getCardAuthorisationRequest() {
        Card card = aValidCard();
        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withGatewayAccountEntity(validGatewayAccount)
                .build();
        return new AuthorisationGatewayRequest(charge, card);
    }

    private GatewayResponse<WorldpayOrderStatusResponse> successfulWorldpayCardAuth(WorldpayPaymentProvider connector) {
        AuthorisationGatewayRequest request = getCardAuthorisationRequest();
        GatewayResponse<WorldpayOrderStatusResponse> response = connector.authorise(request);

        assertTrue(response.isSuccessful());

        return response;
    }

    private WorldpayPaymentProvider getValidWorldpayPaymentProvider() {
        GatewayCredentialsConfig config = getWorldpayConfig();
        return new WorldpayPaymentProvider(
                createGatewayClient(
                        ClientBuilder.newClient(),
                        config.getUrls()
                )
        );
    }

    private GatewayCredentialsConfig getWorldpayConfig() {
        return WORLDPAY_CREDENTIALS;
    }

    private static final GatewayCredentialsConfig WORLDPAY_CREDENTIALS = new GatewayCredentialsConfig() {
        @Override
        public Map<String, String> getUrls() {
            return ImmutableMap.of(TEST.toString(), "https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp");
        }
    };

    private String notificationPayloadForTransaction(String transactionId, String status) throws IOException {
        URL resource = getResource("templates/worldpay/notification.xml");
        return Resources.toString(resource, Charset.defaultCharset())
                .replace("{{transactionId}}", transactionId)
                .replace("{{status}}", status);
    }
}
