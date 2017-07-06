package uk.gov.pay.connector.service.epdq;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import org.apache.http.message.BasicNameValuePair;
import org.hamcrest.core.IsNull;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.model.CancelGatewayRequest;
import uk.gov.pay.connector.model.CaptureGatewayRequest;
import uk.gov.pay.connector.model.GatewayError;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.ClientFactory;
import uk.gov.pay.connector.service.GatewayClient;
import uk.gov.pay.connector.service.GatewayClientFactory;
import uk.gov.pay.connector.service.GatewayOperation;
import uk.gov.pay.connector.service.GatewayOperationClientBuilder;
import uk.gov.pay.connector.service.PaymentGatewayName;
import uk.gov.pay.connector.util.AuthUtils;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.ErrorType.UNEXPECTED_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.service.worldpay.WorldpayPaymentProvider.includeSessionIdentifier;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_NOTIFICATION_TEMPLATE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CAPTURE_SUCCESS_RESPONSE;

@RunWith(MockitoJUnitRunner.class)
public class EpdqPaymentProviderTest {

    public static final String NOTIFICATION_ORDER_ID = "2jhqgrb71f47ftq9u1t5c1143o";
    public static final String NOTIFICATION_STATUS = "9";
    public static final String NOTIFICATION_PAY_ID = "3020450409";
    public static final String NOTIFICATION_SHA_SIGN = "9537B9639F108CDF004459D8A690C598D97506CDF072C3926A60E39759A6402C5089161F6D7A8EA12BBC0FD6F899CE72D5A0C4ACC2913C56ACF6D01B034EEC32";


    private EpdqPaymentProvider provider;
    private GatewayClientFactory gatewayClientFactory;
    private Map<String, String> urlMap = ImmutableMap.of(TEST.toString(), "http://epdq.url");

    EnumMap<GatewayOperation, GatewayClient> gatewayClients;

    @Mock
    private Client mockClient;
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
    @Mock
    private SignatureGenerator mockSignatureGenerator;

    @Mock
    private Notification mockNotification;

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

        provider = new EpdqPaymentProvider(gatewayClients, mockSignatureGenerator);
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

    @Test
    public void shouldVerifyNotificationSignature() {
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(Collections.singletonMap(CREDENTIALS_SHA_OUT_PASSPHRASE, "passphrase"));

        when(mockNotification.getPayload()).thenReturn(Optional.of(Arrays.asList(
            new BasicNameValuePair("key1", "value1"),
            new BasicNameValuePair( "SHASIGN", "signature")
        )));

        when(mockSignatureGenerator.sign(Collections.singletonList(new BasicNameValuePair
            ("key1", "value1")), "passphrase")).thenReturn("signature");

        assertThat(provider.verifyNotification(mockNotification, mockGatewayAccountEntity), is(true));
    }

    @Test
    public void shouldVerifyNotificationSignatureIgnoringCase() {
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(Collections.singletonMap(CREDENTIALS_SHA_OUT_PASSPHRASE, "passphrase"));

        when(mockNotification.getPayload()).thenReturn(Optional.of(Arrays.asList(
            new BasicNameValuePair("key1", "value1"),
            new BasicNameValuePair( "SHASIGN", "SIGNATURE")
        )));

        when(mockSignatureGenerator.sign(Collections.singletonList(new BasicNameValuePair
            ("key1", "value1")), "passphrase")).thenReturn("signature");

        assertThat(provider.verifyNotification(mockNotification, mockGatewayAccountEntity), is(true));
    }

    @Test
    public void shouldNotVerifyNotificationIfWrongSignature() {
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(Collections.singletonMap(CREDENTIALS_SHA_OUT_PASSPHRASE, "passphrase"));

        when(mockNotification.getPayload()).thenReturn(Optional.of(Arrays.asList(
            new BasicNameValuePair("key1", "value1"),
            new BasicNameValuePair( "SHASIGN", "signature")
        )));

        when(mockSignatureGenerator.sign(Collections.singletonList(new BasicNameValuePair
            ("key1", "value1")), "passphrase")).thenReturn("wrong signature");

        assertThat(provider.verifyNotification(mockNotification, mockGatewayAccountEntity), is(false));
    }

    @Test
    public void shouldNotVerifyNotificationIfEmptyPayload() {
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(Collections.singletonMap(CREDENTIALS_SHA_OUT_PASSPHRASE, "passphrase"));

        when(mockNotification.getPayload()).thenReturn(Optional.empty());

        assertThat(provider.verifyNotification(mockNotification, mockGatewayAccountEntity), is(false));
    }

    @Test
    public void parseNotification_shouldReturnNotificationsIfValidFormUrlEncoded() throws IOException  {
        Either<String, Notifications<String>> response =
            provider.parseNotification(notificationPayloadForTransaction(NOTIFICATION_ORDER_ID, NOTIFICATION_STATUS, NOTIFICATION_PAY_ID, NOTIFICATION_SHA_SIGN));

        assertThat(response.isRight(), is(true));

        ImmutableList<Notification<String>> notifications = response.right().value().get();

        assertThat(notifications.size(), is(1));

        Notification<String> notification = notifications.get(0);

        assertThat(notification.getTransactionId(), is(NOTIFICATION_PAY_ID));
        assertThat(notification.getReference(), is(NOTIFICATION_ORDER_ID));
        assertThat(notification.getStatus(), is(NOTIFICATION_STATUS));
        assertThat(notification.getGatewayEventDate(), IsNull.nullValue());
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
            CREDENTIALS_SHA_IN_PASSPHRASE, "sha-passphrase"
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

    private String notificationPayloadForTransaction( String orderId, String status, String payId, String shaSign)
        throws IOException {
        return TestTemplateResourceLoader.load(EPDQ_NOTIFICATION_TEMPLATE)
                .replace("{{orderId}}", orderId)
                .replace("{{status}}", status)
                .replace("{{payId}}", payId)
                .replace("{{shaSign}}", shaSign);
    }
}
