package uk.gov.pay.connector.it.contract;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.PaymentProvider;
import uk.gov.pay.connector.gateway.epdq.EpdqCaptureHandler;
import uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider;
import uk.gov.pay.connector.gateway.epdq.SignatureGenerator;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqAuthorisationResponse;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCaptureResponse;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqRefundResponse;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.TestClientFactory;

import javax.ws.rs.client.Client;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.util.SystemUtils.envOrThrow;

@Ignore("Ignoring as this test is failing in Jenkins because it's failing to locate the certificates - PP-1707")
@RunWith(MockitoJUnitRunner.class)
public class EpdqPaymentProviderTest {

    private String url = "https://mdepayments.epdq.co.uk/ncol/test";
    private String merchantId = envOrThrow("GDS_CONNECTOR_EPDQ_MERCHANT_ID");
    private String username = envOrThrow("GDS_CONNECTOR_EPDQ_USER");
    private String password = envOrThrow("GDS_CONNECTOR_EPDQ_PASSWORD");
    private String shaInPassphrase = envOrThrow("GDS_CONNECTOR_EPDQ_SHA_IN_PASSPHRASE");
    private ChargeEntity chargeEntity;
    private EpdqPaymentProvider paymentProvider;

    @Mock
    private MetricRegistry mockMetricRegistry;

    @Mock
    private Histogram mockHistogram;

    @Mock
    private Environment mockEnvironment;

    @Mock
    private LinksConfig mockLinksConfig;

    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;

    @Mock
    private GatewayConfig mockGatewayConfig;

    @Mock
    private GatewayClientFactory mockGatewayClientFactory;

    @Mock
    private SignatureGenerator mockSignatureGenerator;

    @Before
    public void setUp() {
        when(mockConnectorConfiguration.getLinks()).thenReturn(mockLinksConfig);
        when(mockConnectorConfiguration.getGatewayConfigFor(EPDQ)).thenReturn(mockGatewayConfig);
        when(mockLinksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");
        when(mockGatewayConfig.getUrls()).thenReturn(Collections.emptyMap());
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockEnvironment.metrics()).thenReturn(mockMetricRegistry);

        Client client = TestClientFactory.createJerseyClient();
        GatewayClient gatewayClient = new GatewayClient(client, ImmutableMap.of(TEST.toString(), url),
                EpdqPaymentProvider.includeSessionIdentifier(), mockMetricRegistry);

        when(mockGatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class),
                any(GatewayOperation.class),
                any(Map.class),
                any(BiFunction.class),
                any())).thenReturn(gatewayClient);

        paymentProvider = new EpdqPaymentProvider(mockConnectorConfiguration, mockGatewayClientFactory, mockEnvironment, mockSignatureGenerator);
    }

    @Test
    public void shouldAuthoriseSuccessfully() {
        setUpAndCheckThatEpdqIsUp();
        CardAuthorisationGatewayRequest request = buildAuthorisationRequest(chargeEntity);
        GatewayResponse<EpdqAuthorisationResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    public void shouldAuthoriseSuccessfullyWithNoAddressInRequest() {
        setUpAndCheckThatEpdqIsUp();
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(null)
                .build();

        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);

        GatewayResponse<EpdqAuthorisationResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    public void shouldAuthoriseWith3dsOnSuccessfully() {
        setUpFor3dsAndCheckThatEpdqIsUp();
        CardAuthorisationGatewayRequest request = buildAuthorisationRequest(chargeEntity);
        GatewayResponse<EpdqAuthorisationResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(REQUIRES_3DS));
    }

    @Test
    public void shouldAuthoriseWith3dsOnAndNoAddressInRequestSuccessfully() {
        setUpFor3dsAndCheckThatEpdqIsUp();

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withAddress(null)
                .build();

        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);

        GatewayResponse<EpdqAuthorisationResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(REQUIRES_3DS));
    }

    @Test
    public void shouldCheckAuthorisationStatusSuccessfully() {
        setUpAndCheckThatEpdqIsUp();
        CardAuthorisationGatewayRequest request = buildAuthorisationRequest(chargeEntity);
        GatewayResponse<EpdqAuthorisationResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));

        GatewayResponse<EpdqAuthorisationResponse> queryResponse = paymentProvider.authorise3dsResponse(buildQueryRequest(chargeEntity, Auth3dsDetails.Auth3dsResult.AUTHORISED.name()));
        assertThat(queryResponse.isSuccessful(), is(true));
        assertThat(response.getBaseResponse().get().authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
    }

    @Test
    public void shouldAuthoriseSuccessfullyWhenCardholderNameContainsRightSingleQuotationMark() {
        setUpAndCheckThatEpdqIsUp();
        String cardholderName = "John O’Connor"; // That’s a U+2019 RIGHT SINGLE QUOTATION MARK, not a U+0027 APOSTROPHE

        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder(cardholderName)
                .build();

        CardAuthorisationGatewayRequest request = new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);
        GatewayResponse<EpdqAuthorisationResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));
    }

    @Test
    public void shouldCaptureSuccessfully() {
        setUpAndCheckThatEpdqIsUp();
        EpdqCaptureHandler epdqCaptureHandler = getCaptureHandler(paymentProvider);
        CardAuthorisationGatewayRequest request = buildAuthorisationRequest(chargeEntity);
        GatewayResponse<EpdqAuthorisationResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));

        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));
        CaptureGatewayRequest captureRequest = buildCaptureRequest(chargeEntity, transactionId);
        GatewayResponse<EpdqCaptureResponse> captureResponse = epdqCaptureHandler.capture(captureRequest);
        assertThat(captureResponse.isSuccessful(), is(true));
    }

    private EpdqCaptureHandler getCaptureHandler(PaymentProvider paymentProvider) {
        return (EpdqCaptureHandler) paymentProvider.getCaptureHandler();
    }

    @Test
    public void shouldCancelSuccessfully() {
        setUpAndCheckThatEpdqIsUp();
        CardAuthorisationGatewayRequest request = buildAuthorisationRequest(chargeEntity);
        GatewayResponse<EpdqAuthorisationResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));

        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));
        CancelGatewayRequest cancelRequest = buildCancelRequest(chargeEntity, transactionId);
        GatewayResponse<EpdqCaptureResponse> cancelResponse = paymentProvider.cancel(cancelRequest);
        assertThat(cancelResponse.isSuccessful(), is(true));
    }

    @Test
    public void shouldRefundSuccessfully() {
        setUpAndCheckThatEpdqIsUp();
        EpdqCaptureHandler epdqCaptureHandler = getCaptureHandler(paymentProvider);
        CardAuthorisationGatewayRequest request = buildAuthorisationRequest(chargeEntity);
        GatewayResponse<EpdqAuthorisationResponse> response = paymentProvider.authorise(request);
        assertThat(response.isSuccessful(), is(true));

        String transactionId = response.getBaseResponse().get().getTransactionId();
        assertThat(transactionId, is(not(nullValue())));
        CaptureGatewayRequest captureRequest = buildCaptureRequest(chargeEntity, transactionId);
        GatewayResponse<EpdqCaptureResponse> captureResponse = epdqCaptureHandler.capture(captureRequest);
        assertThat(captureResponse.isSuccessful(), is(true));

        RefundGatewayRequest refundGatewayRequest = buildRefundRequest(chargeEntity, (chargeEntity.getAmount() - 100));
        GatewayResponse<EpdqRefundResponse> refundResponse = paymentProvider.refund(refundGatewayRequest);
        assertThat(refundResponse.isSuccessful(), is(true));
    }

    private static CardAuthorisationGatewayRequest buildAuthorisationRequest(ChargeEntity chargeEntity) {
        AuthCardDetails authCardDetails = AuthCardDetailsFixture.anAuthCardDetails().build();
        return new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);
    }

    private static Auth3dsResponseGatewayRequest buildQueryRequest(ChargeEntity chargeEntity, String auth3DResult) {
        Auth3dsDetails auth3DsDetails = new Auth3dsDetails();
        auth3DsDetails.setAuth3dsResult(auth3DResult);
        return new Auth3dsResponseGatewayRequest(chargeEntity, auth3DsDetails);
    }

    private void setUpFor3dsAndCheckThatEpdqIsUp() {
        epdqSetupWithStatusCheck(true);
    }

    private void setUpAndCheckThatEpdqIsUp() {
        epdqSetupWithStatusCheck(false);
    }

    private void epdqSetupWithStatusCheck(boolean require3ds) {
        try {
            new URL(url).openConnection().connect();
            Map<String, String> validEpdqCredentials = ImmutableMap.of(
                    "merchant_id", merchantId,
                    "username", username,
                    "password", password,
                    "sha_in_passphrase", shaInPassphrase);
            GatewayAccountEntity validGatewayAccount = new GatewayAccountEntity();
            validGatewayAccount.setId(123L);
            validGatewayAccount.setGatewayName("epdq");
            validGatewayAccount.setCredentials(validEpdqCredentials);
            validGatewayAccount.setType(TEST);
            validGatewayAccount.setRequires3ds(require3ds);

            chargeEntity = aValidChargeEntity()
                    .withGatewayAccountEntity(validGatewayAccount)
                    .withTransactionId(randomUUID().toString())
                    .build();

        } catch (IOException ex) {
            Assume.assumeTrue(false);
        }
    }

    private static CaptureGatewayRequest buildCaptureRequest(ChargeEntity chargeEntity, String transactionId) {
        chargeEntity.setGatewayTransactionId(transactionId);
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }

    private static RefundGatewayRequest buildRefundRequest(ChargeEntity chargeEntity, Long refundAmount) {
        return RefundGatewayRequest.valueOf(new RefundEntity(chargeEntity, refundAmount, userExternalId));
    }

    private CancelGatewayRequest buildCancelRequest(ChargeEntity chargeEntity, String transactionId) {
        chargeEntity.setGatewayTransactionId(transactionId);
        return CancelGatewayRequest.valueOf(chargeEntity);
    }
}
