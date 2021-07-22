package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.worldpay.WorldpayAuthoriseHandler;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCaptureHandler;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider;
import uk.gov.pay.connector.gateway.worldpay.WorldpayRefundHandler;
import uk.gov.pay.connector.gateway.worldpay.wallets.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.logging.AuthorisationLogger;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.paymentprocessor.service.CardExecutorService;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import javax.ws.rs.client.ClientBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

@Ignore("Ignoring as this test is failing in Jenkins because it's failing to locate the certificates - PP-1707")
public class WorldpayPaymentProviderTest {

    private static final String MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS = "3D";
    private static final String MAGIC_CARDHOLDER_NAME_FOR_3DS_FLEX_CHALLENGE_REQUIRED_RESPONSE = "3DS_V2_CHALLENGE_IDENTIFIED";

    private GatewayAccountEntity validGatewayAccount;
    private GatewayAccountEntity validGatewayAccountFor3ds;
    private Map<String, String> validCredentials;
    private Map<String, String> validCredentials3ds;
    private ChargeEntity chargeEntity;
    private MetricRegistry mockMetricRegistry;
    private Histogram mockHistogram;
    private Counter mockCounter;
    private Environment mockEnvironment;
    private CardExecutorService mockCardExecutorService = mock(CardExecutorService.class);
    private EventService mockEventService = mock(EventService.class);

    @Before
    public void checkThatWorldpayIsUp() throws IOException {
        try {
            validCredentials = Map.of(
                    "merchant_id", envOrThrow("GDS_CONNECTOR_WORLDPAY_MERCHANT_ID"),
                    "username", envOrThrow("GDS_CONNECTOR_WORLDPAY_USER"),
                    "password", envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD"));

            validCredentials3ds = Map.of(
                    "merchant_id", envOrThrow("GDS_CONNECTOR_WORLDPAY_MERCHANT_ID_3DS"),
                    "username", envOrThrow("GDS_CONNECTOR_WORLDPAY_USER_3DS"),
                    "password", envOrThrow("GDS_CONNECTOR_WORLDPAY_PASSWORD_3DS"));
        } catch (IllegalStateException ex) {
            Assume.assumeTrue("Ignoring test since credentials not configured", false);
        }

        new URL(gatewayUrlMap().get(TEST.toString()).toString()).openConnection().connect();


        validGatewayAccount = new GatewayAccountEntity();
        validGatewayAccount.setId(1234L);
        validGatewayAccount.setType(TEST);
        validGatewayAccount.setGatewayAccountCredentials(List.of(aGatewayAccountCredentialsEntity()
                .withCredentials(validCredentials)
                .withGatewayAccountEntity(validGatewayAccount)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build()));
        validGatewayAccountFor3ds = new GatewayAccountEntity();
        validGatewayAccountFor3ds.setId(1234L);
        validGatewayAccountFor3ds.setType(TEST);
        validGatewayAccountFor3ds.setGatewayAccountCredentials(List.of(aGatewayAccountCredentialsEntity()
                .withCredentials(validCredentials3ds)
                .withGatewayAccountEntity(validGatewayAccountFor3ds)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build()));

        mockMetricRegistry = mock(MetricRegistry.class);
        mockHistogram = mock(Histogram.class);
        mockCounter = mock(Counter.class);
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
    public void submitAuthForSoftDecline() {
        validGatewayAccount.setRequires3ds(true);
        validGatewayAccount.setIntegrationVersion3ds(2);
        validGatewayAccount.setWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build());

        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        // card holder of "EE.REJECTED_ISSUER_REJECTED.SOFT_DECLINED" elicits a soft decline response, see https://developer.worldpay.com/docs/wpg/scaexemptionservices/exemptionengine#testing-exemption-engine
        var authCardDetails = anAuthCardDetails()
                .withCardHolder("EE.REJECTED_ISSUER_REJECTED.SOFT_DECLINED")
                .withWorldpay3dsFlexDdcResult(UUID.randomUUID().toString())
                .build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);
        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getBaseResponse().get().getLastEvent().isPresent());
        assertEquals(response.getBaseResponse().get().getLastEvent().get(), "AUTHORISED");
    }
    
    @Test
    public void submitAuthRequestWithExemptionEngineFlag() {
        validGatewayAccount.setRequires3ds(true);
        validGatewayAccount.setIntegrationVersion3ds(2);
        validGatewayAccount.setWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build());
        
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        // card holder of "EE.HONOURED_ISSUER_HONOURED.AUTHORISED" elicits an authorised response, see https://developer.worldpay.com/docs/wpg/scaexemptionservices/exemptionengine#testing-exemption-engine
        var authCardDetails = anAuthCardDetails()
                .withCardHolder("EE.HONOURED_ISSUER_HONOURED.AUTHORISED")
                .withWorldpay3dsFlexDdcResult(UUID.randomUUID().toString())
                .build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);
        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getBaseResponse().get().getLastEvent().isPresent());
        assertEquals(response.getBaseResponse().get().getLastEvent().get(), "AUTHORISED");
        assertTrue(response.getBaseResponse().get().getExemptionResponseResult().isPresent());
        assertEquals(response.getBaseResponse().get().getExemptionResponseResult().get(), "HONOURED");
        assertEquals(response.getBaseResponse().get().getExemptionResponseReason(), "ISSUER_HONOURED");
    }

    @Test
    public void submit3DS2FlexAuthRequest() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        var authCardDetails = anAuthCardDetails().withWorldpay3dsFlexDdcResult(UUID.randomUUID().toString()).build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);
        assertTrue(response.getBaseResponse().isPresent());
    }

    @Test
    public void submit3DS2FlexAuthRequest_requiresChallenge() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails()
                .withCardHolder(MAGIC_CARDHOLDER_NAME_FOR_3DS_FLEX_CHALLENGE_REQUIRED_RESPONSE)
                .withWorldpay3dsFlexDdcResult(UUID.randomUUID().toString())
                .build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);
        assertTrue(response.getBaseResponse().isPresent());
        response.getBaseResponse().ifPresent(res -> {
            assertThat(res.getGatewayParamsFor3ds().isPresent(), is(true));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengeAcsUrl(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengeTransactionId(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getWorldpayChallengePayload(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getThreeDsVersion(), is(notNullValue()));
        });
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchant() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails().build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);
        assertTrue(response.getBaseResponse().isPresent());
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantWithoutAddress() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails().withAddress(null).build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);
        assertTrue(response.getBaseResponse().isPresent());
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantWithUsAddress() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        Address usAddress = new Address();
        usAddress.setLine1("125 Kingsway");
        usAddress.setLine2("Aviation House");
        usAddress.setPostcode("90210");
        usAddress.setCity("Washington D.C.");
        usAddress.setCountry("US");
        AuthCardDetails authCardDetails = anAuthCardDetails().withAddress(usAddress).build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);
        assertTrue(response.getBaseResponse().isPresent());
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantWithCanadaAddress() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        Address canadaAddress = new Address();
        canadaAddress.setLine1("125 Kingsway");
        canadaAddress.setLine2("Aviation House");
        canadaAddress.setPostcode("X0A0A0");
        canadaAddress.setCity("Arctic Bay");
        canadaAddress.setCountry("CA");
        AuthCardDetails authCardDetails = anAuthCardDetails().withAddress(canadaAddress).build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);
        assertTrue(response.getBaseResponse().isPresent());
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3ds() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails()
                .withCardHolder(MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS)
                .build();

        CardAuthorisationGatewayRequest request = getCardAuthorisationRequestWithRequired3ds(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);

        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getSessionIdentifier().isPresent());
        response.getBaseResponse().ifPresent(res -> {
            assertThat(res.getGatewayParamsFor3ds().isPresent(), is(true));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getPaRequest(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getIssuerUrl(), is(notNullValue()));
        });
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3dsWithoutAddress() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails()
                .withCardHolder(MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS)
                .withAddress(null)
                .build();

        CardAuthorisationGatewayRequest request = getCardAuthorisationRequestWithRequired3ds(authCardDetails);

        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);
        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getSessionIdentifier().isPresent());
        response.getBaseResponse().ifPresent(res -> {
            assertThat(res.getGatewayParamsFor3ds().isPresent(), is(true));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getPaRequest(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getIssuerUrl(), is(notNullValue()));
        });
    }

    @Test
    public void shouldBeAbleToSendAuthorisationRequestForMerchantUsing3dsWithPayerEmail() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();

        validGatewayAccount.setRequires3ds(true);
        validGatewayAccount.setSendPayerEmailToGateway(true);

        ChargeEntity charge = aValidChargeEntity()
                .withTransactionId(randomUUID().toString())
                .withEmail("payer@email.test")
                .withGatewayAccountEntity(validGatewayAccount)
                .build();

        AuthCardDetails authCardDetails = anAuthCardDetails()
                .withCardHolder(MAGIC_CARDHOLDER_NAME_THAT_MAKES_WORLDPAY_TEST_REQUIRE_3DS)
                .build();

        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);

        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);

        assertTrue(response.getBaseResponse().isPresent());
        assertTrue(response.getSessionIdentifier().isPresent());
        response.getBaseResponse().ifPresent(res -> {
            assertThat(res.getGatewayParamsFor3ds().isPresent(), is(true));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getPaRequest(), is(notNullValue()));
            assertThat(res.getGatewayParamsFor3ds().get().toAuth3dsRequiredEntity().getIssuerUrl(), is(notNullValue()));
        });
    }

    /**
     * Worldpay does not care about a successful authorization reference to make a capture request.
     * It simply accepts anything as long as the request is well formed. (And ignores it silently)
     */
    @Test
    public void shouldBeAbleToSendCaptureRequestForMerchant() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        CaptureResponse response = paymentProvider.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldBeAbleToSubmitAPartialRefundAfterACaptureHasBeenSubmitted() {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails().build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);
        String transactionId = response.getBaseResponse().get().getTransactionId();

        assertThat(response.getBaseResponse().isPresent(), is(true));
        assertThat(response.getBaseResponse().isPresent(), is(true));
        assertThat(transactionId, is(not(nullValue())));

        chargeEntity.setGatewayTransactionId(transactionId);
        CaptureResponse captureResponse = paymentProvider.capture(CaptureGatewayRequest.valueOf(chargeEntity));

        assertThat(captureResponse.isSuccessful(), is(true));

        RefundEntity refundEntity = new RefundEntity(1L, userExternalId, userEmail, chargeEntity.getExternalId());

        GatewayRefundResponse refundResponse = paymentProvider.refund(RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, validGatewayAccount));

        assertTrue(refundResponse.isSuccessful());
    }

    @Test
    public void shouldBeAbleToSendCancelRequestForMerchant() throws Exception {
        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();
        AuthCardDetails authCardDetails = anAuthCardDetails().build();
        CardAuthorisationGatewayRequest request = getCardAuthorisationRequest(authCardDetails);
        GatewayResponse<WorldpayOrderStatusResponse> response = paymentProvider.authorise(request);

        assertThat(response.getBaseResponse().isPresent(), is(true));
        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));

        chargeEntity.setGatewayTransactionId(transactionId);

        CancelGatewayRequest cancelGatewayRequest = CancelGatewayRequest.valueOf(chargeEntity);
        GatewayResponse cancelResponse = paymentProvider.cancel(cancelGatewayRequest);

        assertThat(cancelResponse.getBaseResponse().isPresent(), is(true));
    }

    @Test
    public void shouldFailRequestAuthorisationIfCredentialsAreNotCorrect() {

        WorldpayPaymentProvider paymentProvider = getValidWorldpayPaymentProvider();

        Long gatewayAccountId = 112233L;
        var credentials = Map.of(
                "merchant_id", "non-existent-id",
                "username", "non-existent-username",
                "password", "non-existent-password"
        );

        GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity(TEST);
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(aGatewayAccountCredentialsEntity()
                .withCredentials(credentials)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build()));

        gatewayAccountEntity.setId(gatewayAccountId);

        ChargeEntity charge = aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).build();
        AuthCardDetails authCardDetails = anAuthCardDetails().build();
        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(charge, authCardDetails);
        assertFalse(paymentProvider.authorise(request).getBaseResponse().isPresent());
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
        GatewayClient gatewayClient = new GatewayClient(ClientBuilder.newClient(), mockMetricRegistry);
        
        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(
                any(PaymentGatewayName.class),
                any(GatewayOperation.class),
                any(MetricRegistry.class))).thenReturn(gatewayClient);

        return new WorldpayPaymentProvider(
                gatewayUrlMap(), 
                gatewayClient, 
                gatewayClient,
                gatewayClient, 
                new WorldpayWalletAuthorisationHandler(gatewayClient, gatewayUrlMap()), 
                new WorldpayAuthoriseHandler(gatewayClient, gatewayUrlMap()), 
                new WorldpayCaptureHandler(gatewayClient, gatewayUrlMap()),
                new WorldpayRefundHandler(gatewayClient, gatewayUrlMap()), 
                new AuthorisationService(mockCardExecutorService, mockEnvironment), 
                new AuthorisationLogger(new AuthorisationRequestSummaryStringifier(), new AuthorisationRequestSummaryStructuredLogging()), 
                mock(ChargeDao.class),
                mock(EventService.class));
    }

    private Map<String, URI> gatewayUrlMap() {
        return Map.of(TEST.toString(), URI.create("https://secure-test.worldpay.com/jsp/merchant/xml/paymentService.jsp"));
    }
}
