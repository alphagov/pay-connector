package uk.gov.pay.connector.gateway.epdq;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.mockito.Mock;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.LinksConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_STATUS_DECLINED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_STATUS_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_SUCCESS_3D_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CAPTURE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_DELETE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_NOTIFICATION_TEMPLATE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_REFUND_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_REFUND_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_REFUND_SUCCESS_RESPONSE;

public abstract class BaseEpdqPaymentProviderTest {
    static final String NOTIFICATION_STATUS = "9";
    static final String NOTIFICATION_PAY_ID = "3020450409";
    static final String NOTIFICATION_PAY_ID_SUB = "2";
    static final String NOTIFICATION_SHA_SIGN = "9537B9639F108CDF004459D8A690C598D97506CDF072C3926A60E39759A6402C5089161F6D7A8EA12BBC0FD6F899CE72D5A0C4ACC2913C56ACF6D01B034protected";

    protected GatewayClientFactory gatewayClientFactory;
    protected EpdqPaymentProvider provider;


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
    ClientFactory mockClientFactory;
    @Mock
    SignatureGenerator mockSignatureGenerator;
    @Mock
    Environment environment;
    @Mock
    ConnectorConfiguration configuration;
    @Mock
    Notification mockNotification;
    @Mock
    GatewayConfig gatewayConfig;
    @Mock
    LinksConfig linksConfig;

    Invocation.Builder mockClientInvocationBuilder;

    @Before
    public void setup() {
        gatewayClientFactory = new GatewayClientFactory(mockClientFactory);

        mockClientInvocationBuilder = mockClientInvocationBuilder();
        when(environment.metrics()).thenReturn(mockMetricRegistry);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockClientFactory.createWithDropwizardClient(
                eq(PaymentGatewayName.EPDQ), any(GatewayOperation.class), any(MetricRegistry.class))
        ).thenReturn(mockClient);

        when(configuration.getGatewayConfigFor(PaymentGatewayName.EPDQ)).thenReturn(gatewayConfig);
        when(gatewayConfig.getUrls()).thenReturn(ImmutableMap.of(TEST.toString(), "http://epdq.url"));

        when(configuration.getLinks()).thenReturn(linksConfig);
        when(linksConfig.getFrontendUrl()).thenReturn("http://frontendUrl");

        provider = new EpdqPaymentProvider(configuration, gatewayClientFactory, environment, mockSignatureGenerator);
    }

    private Invocation.Builder mockClientInvocationBuilder() {
        Invocation.Builder mockClientInvocationBuilder = mock(Invocation.Builder.class);
        when(mockClientInvocationBuilder.header(anyString(), any(Object.class))).thenReturn(mockClientInvocationBuilder);

        WebTarget mockTarget = mock(WebTarget.class);
        when(mockTarget.request()).thenReturn(mockClientInvocationBuilder);
        when(mockClient.target(anyString())).thenReturn(mockTarget);

        return mockClientInvocationBuilder;
    }

    void verifyPaymentProviderRequest(String requestPayload) {
        verify(mockClientInvocationBuilder).post(Entity.entity(requestPayload,
                MediaType.APPLICATION_FORM_URLENCODED));
    }

    String successAuthRequest() {
        return TestTemplateResourceLoader.load(TestTemplateResourceLoader.EPDQ_AUTHORISATION_REQUEST);
    }

    String successAuthQueryRequest() {
        return TestTemplateResourceLoader.load(TestTemplateResourceLoader.EPDQ_AUTHORISATION_STATUS_REQUEST);
    }

    String successAuthResponse() {
        return TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_SUCCESS_RESPONSE);
    }

    String successAuth3dResponse() {
        return TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_SUCCESS_3D_RESPONSE);
    }

    String successAuthorisedQueryResponse() {
        return TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_SUCCESS_RESPONSE);
    }

    String declinedAuthorisedQueryResponse() {
        return TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_STATUS_DECLINED_RESPONSE);
    }

    String errorAuthorisedQueryResponse() {
        return TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_STATUS_ERROR_RESPONSE);
    }

    String errorAuthResponse() {
        return TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_ERROR_RESPONSE);
    }

    String successCaptureResponse() {
        return TestTemplateResourceLoader.load(EPDQ_CAPTURE_SUCCESS_RESPONSE);
    }

    String errorCaptureResponse() {
        return TestTemplateResourceLoader.load(EPDQ_CAPTURE_ERROR_RESPONSE);
    }

    String successCaptureRequest() {
        return TestTemplateResourceLoader.load(TestTemplateResourceLoader.EPDQ_CAPTURE_REQUEST);
    }

    String successCancelResponse() {
        return TestTemplateResourceLoader.load(EPDQ_CANCEL_SUCCESS_RESPONSE);
    }

    String errorCancelResponse() {
        return TestTemplateResourceLoader.load(EPDQ_CANCEL_ERROR_RESPONSE);
    }

    String successCancelRequest() {
        return TestTemplateResourceLoader.load(EPDQ_CANCEL_REQUEST);
    }

    String successRefundResponse() {
        return TestTemplateResourceLoader.load(EPDQ_REFUND_SUCCESS_RESPONSE);
    }

    String errorRefundResponse() {
        return TestTemplateResourceLoader.load(EPDQ_REFUND_ERROR_RESPONSE);
    }

    String successRefundRequest() {
        return TestTemplateResourceLoader.load(EPDQ_REFUND_REQUEST);
    }

    String successDeletionResponse() {
        return TestTemplateResourceLoader.load(EPDQ_DELETE_SUCCESS_RESPONSE);
    }

    String notificationPayloadForTransaction(String status, String payId, String payIdSub, String shaSign)
            throws IOException {
        return TestTemplateResourceLoader.load(EPDQ_NOTIFICATION_TEMPLATE)
                .replace("{{status}}", status)
                .replace("{{payId}}", payId)
                .replace("{{payIdSub}}", payIdSub)
                .replace("{{shaSign}}", shaSign);
    }

    AuthorisationGatewayRequest buildTestAuthorisationRequest(ChargeEntity chargeEntity) {
        return new AuthorisationGatewayRequest(chargeEntity, buildTestAuthCardDetails());
    }

    CaptureGatewayRequest buildTestCaptureRequest(ChargeEntity chargeEntity) {
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }

    CancelGatewayRequest buildTestCancelRequest(ChargeEntity chargeEntity) {
        return CancelGatewayRequest.valueOf(chargeEntity);
    }

    RefundGatewayRequest buildTestRefundRequest(ChargeEntity chargeEntity) {
        return RefundGatewayRequest.valueOf(new RefundEntity(chargeEntity, chargeEntity.getAmount() - 100, userExternalId));
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

    AuthorisationGatewayRequest buildTestAuthorisationRequest() {
        return buildTestAuthorisationRequest(buildTestGatewayAccountEntity());
    }

    Auth3dsResponseGatewayRequest buildTestAuthorisation3dsVerifyRequest(String auth3dsFrontendResult) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(buildTestGatewayAccountEntity())
                .withExternalId("mq4ht90j2oir6am585afk58kml")
                .withTransactionId("payId")
                .build();
        Auth3dsDetails auth3DsDetails = new Auth3dsDetails();
        auth3DsDetails.setAuth3dsResult(auth3dsFrontendResult);
        return new Auth3dsResponseGatewayRequest(chargeEntity, auth3DsDetails);
    }

    CaptureGatewayRequest buildTestCaptureRequest() {
        return buildTestCaptureRequest(buildTestGatewayAccountEntity());
    }

    CancelGatewayRequest buildTestCancelRequest() {
        return buildTestCancelRequest(buildTestGatewayAccountEntity());
    }

    RefundGatewayRequest buildTestRefundRequest() {
        return buildTestRefundRequest(buildTestGatewayAccountEntity());
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

    private RefundGatewayRequest buildTestRefundRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(accountEntity)
                .withTransactionId("payId")
                .build();
        return buildTestRefundRequest(chargeEntity);
    }

    private AuthCardDetails buildTestAuthCardDetails() {
        Address address = new Address("41", "Scala Street", "EC2A 1AE", "London", null, "GB");
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Mr. Payment")
                .withCardNo("5555444433331111")
                .withCvc("737")
                .withEndDate("08/18")
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }

    void assertEquals(GatewayError actual, GatewayError expected) {
        assertNotNull(actual);
        assertThat(actual.getMessage(), is(expected.getMessage()));
        assertThat(actual.getErrorType(), is(expected.getErrorType()));
    }

    void mockPaymentProviderResponse(int responseHttpStatus, String responsePayload) {
        Response response = mock(Response.class);
        when(mockClientInvocationBuilder.post(any())).thenReturn(response);

        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(response.getStatus()).thenReturn(responseHttpStatus);
    }
}
