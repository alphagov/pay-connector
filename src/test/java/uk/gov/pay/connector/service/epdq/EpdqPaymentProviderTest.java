package uk.gov.pay.connector.service.epdq;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import javax.ws.rs.core.MediaType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.*;
import uk.gov.pay.connector.util.AuthUtils;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.EnumMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.*;
import static uk.gov.pay.connector.model.ErrorType.UNEXPECTED_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.GatewayAccount.*;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider.includeSessionIdentifier;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.*;

@RunWith(MockitoJUnitRunner.class)
public class EpdqPaymentProviderTest {

    private EpdqPaymentProvider provider;
    private GatewayClientFactory gatewayClientFactory;
    private Map<String, String> urlMap = ImmutableMap.of(TEST.toString(), "http://epdq.url");

    EnumMap<GatewayOperation, GatewayClient> gatewayClients;

    @Mock
    private Client mockClient;
    @Mock
    private GatewayClient mockGatewayClient;
    @Mock
    GatewayAccountEntity mockGatewayAccountEntity;
    @Mock
    MetricRegistry mockMetricRegistry;
    @Mock
    Histogram mockHistogram;
    @Mock
    Counter mockCounter;
    @Mock
    private ClientFactory mockClientFactory;

    private Invocation.Builder mockClientInvocationBuilder;

    @Before
    public void setup() throws Exception {
        mockClientInvocationBuilder = mockClientInvocationBuilder();
        gatewayClientFactory = new GatewayClientFactory(mockClientFactory);

        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockClientFactory.createWithDropwizardClient(
                        eq(PaymentGatewayName.EPDQ), any(GatewayOperation.class), any(MetricRegistry.class))
        )
                .thenReturn(mockClient);

        GatewayClient authClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.EPDQ, GatewayOperation.AUTHORISE,
                urlMap, includeSessionIdentifier(), mockMetricRegistry);
        GatewayClient cancelClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.EPDQ, GatewayOperation.CANCEL,
                urlMap, includeSessionIdentifier(), mockMetricRegistry);
        GatewayClient refundClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.EPDQ, GatewayOperation.REFUND,
                urlMap, includeSessionIdentifier(), mockMetricRegistry);
        GatewayClient captureClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.EPDQ, GatewayOperation.CAPTURE,
                urlMap, includeSessionIdentifier(), mockMetricRegistry);

        gatewayClients = GatewayOperationClientBuilder.builder()
                .authClient(authClient)
                .captureClient(captureClient)
                .cancelClient(cancelClient)
                .refundClient(refundClient)
                .build();

        provider = new EpdqPaymentProvider(gatewayClients);
    }

    @Test
    public void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName(), is("epdq"));
    }

    @Test
    public void shouldGenerateNoTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Test
    public void shouldAuthorise() {
        mockPaymentProviderResponse(200, successAuthResponse());
        GatewayResponse<EpdqAuthorisationResponse> response = provider.authorise(buildTestAuthorisationRequest());
        verifyPaymentProviderRequest(successAuthRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("3014644340"));
    }

    @Test
    public void shouldNotAuthoriseIfPaymentProviderReturnsUnexpectedStatusCode() {
        mockPaymentProviderResponse(400, errorAuthResponse());
        GatewayResponse<EpdqAuthorisationResponse> response = provider.authorise(buildTestAuthorisationRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected Response Code From Gateway", UNEXPECTED_STATUS_CODE_FROM_GATEWAY));
    }

    @Test
    public void shouldCapture() {
        mockPaymentProviderResponse(200, successCaptureResponse());
        GatewayResponse<EpdqCaptureResponse> response = provider.capture(buildTestCaptureRequest());
        verifyPaymentProviderRequest(successCaptureRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("3014644340"));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturnsUnexpectedStatusCode() {
        mockPaymentProviderResponse(400, errorAuthResponse());
        GatewayResponse<EpdqCaptureResponse> response = provider.capture(buildTestCaptureRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected Response Code From Gateway", UNEXPECTED_STATUS_CODE_FROM_GATEWAY));
    }

    @Test
    public void shouldCancel() {
        mockPaymentProviderResponse(200, successCancelResponse());
        GatewayResponse<EpdqCancelResponse> response = provider.cancel(buildTestCancelRequest());
        verifyPaymentProviderRequest(successCancelRequest());
        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().get().getTransactionId(), is("3014644340"));
    }

    @Test
    public void shouldNotCancelIfPaymentProviderReturnsUnexpectedStatusCode() {
        mockPaymentProviderResponse(400, errorAuthResponse());
        GatewayResponse<EpdqCaptureResponse> response = provider.cancel(buildTestCancelRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected Response Code From Gateway", UNEXPECTED_STATUS_CODE_FROM_GATEWAY));
    }

    private GatewayAccountEntity buildTestGatewayAccountEntity() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("epdq");
        gatewayAccount.setRequires3ds(false);
        gatewayAccount.setCredentials(ImmutableMap.of(
                CREDENTIALS_MERCHANT_ID, "merchant-id",
                CREDENTIALS_USERNAME, "username",
                CREDENTIALS_PASSWORD, "password",
                CREDENTIALS_SHA_PASSPHRASE, "sha-passphrase"
        ));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }

    private AuthorisationGatewayRequest buildTestAuthorisationRequest() {
        return buildTestAuthorisationRequest(buildTestGatewayAccountEntity());
    }

    private CaptureGatewayRequest buildTestCaptureRequest() {
        return buildTestCaptureRequest(buildTestGatewayAccountEntity());
    }

    private CancelGatewayRequest buildTestCancelRequest() {
        return buildTestCancelRequest(buildTestGatewayAccountEntity());
    }

    private AuthorisationGatewayRequest buildTestAuthorisationRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withGatewayAccountEntity(accountEntity)
                .build();
        return buildTestAuthorisationRequest(chargeEntity);
    }

    private CaptureGatewayRequest buildTestCaptureRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(accountEntity)
                .withTransactionId("payId")
                .build();
        return buildTestCaptureRequest(chargeEntity);
    }

    private CancelGatewayRequest buildTestCancelRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(accountEntity)
                .withTransactionId("payId")
                .build();
        return buildTestCancelRequest(chargeEntity);
    }

    private AuthCardDetails buildTestAuthCardDetails() {
        Address address = anAddress();
        address.setLine1("123 My Street");
        address.setLine2("This road");
        address.setPostcode("SW8URR");
        address.setCity("London");
        address.setCounty("London state");
        address.setCountry("GB");

        return AuthUtils.buildAuthCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", address);
    }

    private AuthorisationGatewayRequest buildTestAuthorisationRequest(ChargeEntity chargeEntity) {
        return new AuthorisationGatewayRequest(chargeEntity, buildTestAuthCardDetails());
    }

    private CaptureGatewayRequest buildTestCaptureRequest(ChargeEntity chargeEntity) {
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }

    private CancelGatewayRequest buildTestCancelRequest(ChargeEntity chargeEntity) {
        return CancelGatewayRequest.valueOf(chargeEntity);
    }


    private void assertEquals(GatewayError actual, GatewayError expected) {
        assertNotNull(actual);
        assertThat(actual.getMessage(), is(expected.getMessage()));
        assertThat(actual.getErrorType(), is(expected.getErrorType()));
    }

    private Invocation.Builder mockClientInvocationBuilder() {
        Invocation.Builder mockClientInvocationBuilder = mock(Invocation.Builder.class);
        when(mockClientInvocationBuilder.header(anyString(), anyObject())).thenReturn(mockClientInvocationBuilder);

        WebTarget mockTarget = mock(WebTarget.class);
        when(mockTarget.request()).thenReturn(mockClientInvocationBuilder);
        when(mockClient.target(anyString())).thenReturn(mockTarget);

        return mockClientInvocationBuilder;
    }

    private void mockPaymentProviderResponse(int responseHttpStatus, String responsePayload) {
        Response response = mock(Response.class);
        when(mockClientInvocationBuilder.post(any())).thenReturn(response);

        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(response.getStatus()).thenReturn(responseHttpStatus);
    }

    private void verifyPaymentProviderRequest(String requestPayload) {
        verify(mockClientInvocationBuilder).post(Entity.entity(requestPayload,
            MediaType.APPLICATION_FORM_URLENCODED));
    }

    private String successAuthRequest() {
        return TestTemplateResourceLoader.load(TestTemplateResourceLoader.EPDQ_AUTHORISATION_REQUEST);
    }

    private String successAuthResponse() {
        return TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_SUCCESS_RESPONSE);
    }

    private String errorAuthResponse() {
        return TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_ERROR_RESPONSE);
    }

    private String successCaptureResponse() {
        return TestTemplateResourceLoader.load(EPDQ_CAPTURE_SUCCESS_RESPONSE);
    }

    private String successCaptureRequest() {
        return TestTemplateResourceLoader.load(TestTemplateResourceLoader.EPDQ_CAPTURE_REQUEST);
    }

    private String successCancelResponse() {
        return TestTemplateResourceLoader.load(EPDQ_CANCEL_SUCCESS_RESPONSE);
    }


    private String successCancelRequest() {
        return TestTemplateResourceLoader.load(EPDQ_CANCEL_REQUEST);
    }
}
