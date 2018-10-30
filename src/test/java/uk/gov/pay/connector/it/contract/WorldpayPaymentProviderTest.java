package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.DefaultExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCaptureResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.AuthUtils;

import javax.ws.rs.client.ClientBuilder;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.function.BiFunction;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.util.AuthUtils.aValidAuthorisationDetails;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

@Ignore("Ignoring as this test is failing in Jenkins because it's failing to locate the certificates - PP-1707")
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
    private ExternalRefundAvailabilityCalculator defaultExternalRefundAvailabilityCalculator = new DefaultExternalRefundAvailabilityCalculator();

    @Before
    public void checkThatWorldpayIsUp() throws IOException {
        try {
            validCredentials = ImmutableMap.of(
                    "merchant_id", "MERCHANTCODE",
                    "username", envOrThrow("GDS_CONNECTOR_WORLDPAY_USER"),
                    "password", envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD"));

            validCredentials3ds = ImmutableMap.of(
                    "merchant_id", "MERCHANTCODETEST3DS",
                    "username", envOrThrow("GDS_CONNECTOR_WORLDPAY_USER_3DS"),
                    "password", envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD_3DS"));
        } catch (IllegalStateException ex) {
            Assume.assumeTrue("Ignoring test since credentials not configured", false);
        }

        new URL(getWorldpayConfig().getUrls().get(TEST.toString())).openConnection().connect();

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
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchant() throws Exception {
        WorldpayPaymentProvider connector = getValidWorldpayPaymentProvider();
        successfulWorldpayCardAuth(connector);
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3ds() throws Exception {
        WorldpayPaymentProvider connector = getValidWorldpayPaymentProvider();
        GatewayResponse<WorldpayOrderStatusResponse> response = successfulWorldpayCardAuthFor3ds(connector);

        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getSessionIdentifier().isPresent());
        response.getBaseResponse().ifPresent(res -> {
            assertThat(res.getGatewayParamsFor3ds().isPresent(), is(true));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsDetailsEntity().getPaRequest(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsDetailsEntity().getIssuerUrl(), is(notNullValue()));
        });
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
        assertTrue(response.getSessionIdentifier().isPresent());
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

        RefundEntity refundEntity = new RefundEntity(chargeEntity, 1L, userExternalId);

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

        WorldpayPaymentProvider connector = getValidWorldpayPaymentProvider();

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
        charge.getGatewayAccount().setRequires3ds(true);
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
        GatewayClient gatewayClient = new GatewayClient(
                ClientBuilder.newClient(),
                getWorldpayConfig().getUrls(),
            WorldpayPaymentProvider.includeSessionIdentifier(),
                mockMetricRegistry
        );

        ConnectorConfiguration configuration = mock(ConnectorConfiguration.class);
        when(configuration.getGatewayConfigFor(PaymentGatewayName.WORLDPAY)).thenReturn(getWorldpayConfig());
        
        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any(Map.class), any(BiFunction.class), null)).thenReturn(gatewayClient);
        
        return new WorldpayPaymentProvider(configuration, gatewayClientFactory, null);
    }

    private GatewayConfig getWorldpayConfig() {
        return WORLDPAY_CREDENTIALS;
    }

    private static final GatewayConfig WORLDPAY_CREDENTIALS = new GatewayConfig() {
        @Override
        public Map<String, String> getUrls() {
            return ImmutableMap.of(TEST.toString(), "https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp");
        }
    };
}
