package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.paymentprocessor.service.PaymentProviderAuthorisationResponse;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import javax.ws.rs.client.ClientBuilder;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.function.BiFunction;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
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
    private Environment mockEnvironment;

    @Before
    public void checkThatWorldpayIsUp() throws IOException {
        try {
            validCredentials = ImmutableMap.of(
                    "merchant_id", envOrThrow("GDS_CONNECTOR_WORLDPAY_MERCHANT_ID"),
                    "username", envOrThrow("GDS_CONNECTOR_WORLDPAY_USER"),
                    "password", envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD"));

            validCredentials3ds = ImmutableMap.of(
                    "merchant_id", envOrThrow("GDS_CONNECTOR_WORLDPAY_MERCHANT_ID_3DS"),
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
        Histogram mockHistogram = mock(Histogram.class);
        Counter mockCounter = mock(Counter.class);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        mockEnvironment = mock(Environment.class);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);

        chargeEntity = aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withGatewayAccountEntity(validGatewayAccount)
                .build();
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchant() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        PaymentProviderAuthorisationResponse response = paymentProvider.authorise(request);
        assertTrue(response.getAuthoriseStatus().isPresent());
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantWithoutAddress() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(null)
                .build();

        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        PaymentProviderAuthorisationResponse response = paymentProvider.authorise(request);

        assertTrue(response.getAuthoriseStatus().isPresent());
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3ds() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder(MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS)
                .build();

        CardAuthorisationGatewayRequest request = getCardAuthorisationRequestWithRequired3ds(authCardDetails);
        PaymentProviderAuthorisationResponse response = paymentProvider.authorise(request);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AUTHORISED));
        assertTrue(response.getSessionIdentifier().isPresent());
        assertThat(response.getAuth3dsDetailsEntity().isPresent(), is(true));
        assertThat(response.getAuth3dsDetailsEntity().get().getPaRequest(), is(notNullValue()));
        assertThat(response.getAuth3dsDetailsEntity().get().getIssuerUrl(), is(notNullValue()));
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3dsWithoutAddress() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder(MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS)
                .withAddress(null)
                .build();

        CardAuthorisationGatewayRequest request = getCardAuthorisationRequestWithRequired3ds(authCardDetails);

        PaymentProviderAuthorisationResponse response = paymentProvider.authorise(request);
        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AUTHORISED));
        assertTrue(response.getSessionIdentifier().isPresent());
        assertThat(response.getAuth3dsDetailsEntity().isPresent(), is(true));
        assertThat(response.getAuth3dsDetailsEntity().get().getPaRequest(), is(notNullValue()));
        assertThat(response.getAuth3dsDetailsEntity().get().getIssuerUrl(), is(notNullValue()));
    }

    /**
     * Worldpay does not care about a successful authorization reference to make a capture request.
     * It simply accepts anything as long as the request is well formed. (And ignores it silently)
     */
    @Test
    public void shouldBeAbleToSendCaptureRequestForMerchant() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        GatewayResponse response = paymentProvider.capture(CaptureGatewayRequest.valueOf(chargeEntity));

        assertTrue(response.isSuccessful());
        assertTrue(response.getSessionIdentifier().isPresent());
    }

    @Test
    public void shouldBeAbleToSubmitAPartialRefundAfterACaptureHasBeenSubmitted() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        PaymentProviderAuthorisationResponse response = paymentProvider.authorise(request);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AUTHORISED));

        assertTrue(response.getTransactionId().isPresent());
        String transactionId = response.getTransactionId().get();

        chargeEntity.setGatewayTransactionId(transactionId);
        GatewayResponse captureResponse = paymentProvider.capture(CaptureGatewayRequest.valueOf(chargeEntity));

        assertThat(captureResponse.isSuccessful(), is(true));

        RefundEntity refundEntity = new RefundEntity(chargeEntity, 1L, userExternalId);

        GatewayResponse refundGatewayResponse = paymentProvider.refund(RefundGatewayRequest.valueOf(refundEntity));

        assertTrue(refundGatewayResponse.isSuccessful());
    }

    @Test
    public void shouldBeAbleToSendCancelRequestForMerchant() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        PaymentProviderAuthorisationResponse response = paymentProvider.authorise(request);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AUTHORISED));
        assertTrue(response.getTransactionId().isPresent());
        String transactionId = response.getTransactionId().get();

        chargeEntity.setGatewayTransactionId(transactionId);

        CancelGatewayRequest cancelGatewayRequest = CancelGatewayRequest.valueOf(chargeEntity);
        GatewayResponse cancelResponse = paymentProvider.cancel(cancelGatewayRequest);

        assertThat(cancelResponse.isSuccessful(), is(true));
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() {

        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();

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

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();

        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        PaymentProviderAuthorisationResponse response = paymentProvider.authorise(request);

        assertTrue(response.getAuthoriseStatus().isPresent());
        assertThat(response.getAuthoriseStatus().get(), is(AUTHORISED));
    }

    private CardAuthorisationGatewayRequest getCardAuthorisationRequest(AuthCardDetails authCardDetails) {
        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withGatewayAccountEntity(validGatewayAccount)
                .build();
        return new CardAuthorisationGatewayRequest(charge, authCardDetails);
    }

    private CardAuthorisationGatewayRequest getCardAuthorisationRequestWithRequired3ds(AuthCardDetails authCardDetails) {
        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withGatewayAccountEntity(validGatewayAccountFor3ds)
                .build();
        charge.getGatewayAccount().setRequires3ds(true);
        return new CardAuthorisationGatewayRequest(charge, authCardDetails);
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
        when(configuration.getWorldpayConfig()).thenReturn(getWorldpayConfig());

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(
                any(PaymentGatewayName.class),
                any(GatewayOperation.class),
                any(Map.class),
                any(BiFunction.class),
                any(MetricRegistry.class))).thenReturn(gatewayClient);

        return new WorldpayPaymentProvider(configuration, gatewayClientFactory, mockEnvironment);
    }

    private WorldpayConfig getWorldpayConfig() {
        return WORLDPAY_CREDENTIALS;
    }

    private static final WorldpayConfig WORLDPAY_CREDENTIALS = new WorldpayConfig() {
        @Override
        public Map<String, String> getUrls() {
            return ImmutableMap.of(TEST.toString(), "https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp");
        }
    };
}
