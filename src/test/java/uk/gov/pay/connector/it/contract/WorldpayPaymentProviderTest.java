package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.app.GatewayCredentialsConfig;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.RefundGatewayRequest;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayCaptureResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider;
import uk.gov.pay.connector.util.AuthUtils;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.service.GatewayClient.createGatewayClient;
import static uk.gov.pay.connector.util.AuthUtils.aValidAuthorisationDetails;

import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

public class WorldpayPaymentProviderTest {

    private static final String MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS = "3D";

    private GatewayAccountEntity validGatewayAccount;
    private GatewayAccountEntity validGatewayAccountFor3ds;
    private Map<String, String> validCredentials;
    private Map<String, String> validCredentials3ds;
    private ChargeEntity chargeEntity;
    private MetricRegistry mockMetricRegistry;
    private Histogram mockHistogram;
    private Counter mockCounter;

    @Before
    public void checkThatWorldpayIsUp() {
        try {
            new URL(getWorldpayConfig().getUrls().get(TEST.toString())).openConnection().connect();

            validCredentials = ImmutableMap.of(
                    "merchant_id", "MERCHANTCODE",
                    "username", envOrThrow("GDS_CONNECTOR_WORLDPAY_USER"),
                    "password", envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD"));

            validCredentials3ds = ImmutableMap.of(
                    "merchant_id", "MERCHANTCODETEST3DS",
                    "username", envOrThrow("GDS_CONNECTOR_WORLDPAY_USER_3DS"),
                    "password", envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD_3DS"));

            validGatewayAccount = new GatewayAccountEntity();
            validGatewayAccount.setId(1234L);
            validGatewayAccount.setGatewayName("worldpay");
            validGatewayAccount.setCredentials(validCredentials);
            validGatewayAccount.setType(TEST);

            validGatewayAccountFor3ds = new GatewayAccountEntity();
            validGatewayAccountFor3ds.setId(1234L);
            validGatewayAccountFor3ds.setGatewayName("worldpay");
            validGatewayAccountFor3ds.setCredentials(validCredentials3ds);
            validGatewayAccountFor3ds.setType(TEST);

            mockMetricRegistry = mock(MetricRegistry.class);
            mockHistogram = mock(Histogram.class);
            mockCounter = mock(Counter.class);
            when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
            when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);

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

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3ds() throws Exception {
        WorldpayPaymentProvider connector = getValidWorldpayPaymentProvider();
        successfulWorldpayCardAuthFor3ds(connector);
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

        RefundEntity refundEntity = new RefundEntity(chargeEntity, 1L);

        GatewayResponse refundGatewayResponse = connector.refund(RefundGatewayRequest.valueOf(refundEntity));

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

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() throws Exception {

        WorldpayPaymentProvider connector = new WorldpayPaymentProvider(
                createGatewayClient(ClientBuilder.newClient(), getWorldpayConfig().getUrls(), MediaType.APPLICATION_XML_TYPE, mockMetricRegistry)
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

        AuthorisationGatewayRequest request = new AuthorisationGatewayRequest(charge, aValidAuthorisationDetails());
        GatewayResponse<WorldpayOrderStatusResponse> response = connector.authorise(request);

        assertFalse(response.isSuccessful());
    }

    private AuthorisationGatewayRequest getCardAuthorisationRequest() {
        AuthCardDetails authCardDetails = aValidAuthorisationDetails();
        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withGatewayAccountEntity(validGatewayAccount)
                .build();
        return new AuthorisationGatewayRequest(charge, authCardDetails);
    }

    private AuthorisationGatewayRequest getCardAuthorisationRequestWithRequired3ds() {
        AuthCardDetails authCardDetails = AuthUtils.buildAuthCardDetails(MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS);
        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withGatewayAccountEntity(validGatewayAccountFor3ds)
                .build();
        return new AuthorisationGatewayRequest(charge, authCardDetails);
    }

    private GatewayResponse<WorldpayOrderStatusResponse> successfulWorldpayCardAuth(WorldpayPaymentProvider connector) {
        AuthorisationGatewayRequest request = getCardAuthorisationRequest();
        GatewayResponse<WorldpayOrderStatusResponse> response = connector.authorise(request);

        assertTrue(response.isSuccessful());

        return response;
    }

    private GatewayResponse<WorldpayOrderStatusResponse> successfulWorldpayCardAuthFor3ds(WorldpayPaymentProvider connector) {
        AuthorisationGatewayRequest request = getCardAuthorisationRequestWithRequired3ds();
        GatewayResponse<WorldpayOrderStatusResponse> response = connector.authorise(request);

        assertTrue(response.isSuccessful());

        return response;
    }

    private WorldpayPaymentProvider getValidWorldpayPaymentProvider() {
        GatewayCredentialsConfig config = getWorldpayConfig();
        return new WorldpayPaymentProvider(
                createGatewayClient(
                        ClientBuilder.newClient(),
                        config.getUrls(),
                        MediaType.APPLICATION_XML_TYPE,
                        mockMetricRegistry
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
}
