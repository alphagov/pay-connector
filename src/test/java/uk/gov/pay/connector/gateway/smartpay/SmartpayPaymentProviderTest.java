package uk.gov.pay.connector.gateway.smartpay;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import fj.data.Either;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.GatewayOperationClientBuilder;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gateway.worldpay.WorldpayCaptureResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.Notification;
import uk.gov.pay.connector.model.Notifications;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Auth3dsDetails;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.util.AuthUtils;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;

import static com.google.common.io.Resources.getResource;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_AUTHORISATION_3DS_REQUIRED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_CAPTURE_SUCCESS_RESPONSE;

@RunWith(MockitoJUnitRunner.class)
public class SmartpayPaymentProviderTest {

    private SmartpayPaymentProvider provider;
    private GatewayClientFactory gatewayClientFactory;
    private Map<String, String> urlMap = ImmutableMap.of(TEST.toString(), "http://smartpay.url");

    @Mock
    private Client mockClient;
    @Mock
    private MetricRegistry mockMetricRegistry;
    @Mock
    private Histogram mockHistogram;
    @Mock
    private Counter mockCounter;
    @Mock
    private BiFunction<GatewayOrder, Builder, Builder> mockSessionIdentifier;
    @Mock
    private ClientFactory mockClientFactory;
    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;
    @Mock
    private ExternalRefundAvailabilityCalculator mockExternalRefundAvailabilityCalculator;

    @Before
    public void setup() throws Exception {
        gatewayClientFactory = new GatewayClientFactory(mockClientFactory);

        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockClientFactory.createWithDropwizardClient(eq(PaymentGatewayName.SMARTPAY), any(GatewayOperation.class), any(MetricRegistry.class)))
                .thenReturn(mockClient);

        mockSmartpaySuccessfulOrderSubmitResponse();

        GatewayClient authClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.SMARTPAY, GatewayOperation.AUTHORISE,
                urlMap, mockSessionIdentifier, mockMetricRegistry);
        GatewayClient cancelClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.SMARTPAY, GatewayOperation.CANCEL,
                urlMap, mockSessionIdentifier, mockMetricRegistry);
        GatewayClient refundClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.SMARTPAY, GatewayOperation.REFUND,
                urlMap, mockSessionIdentifier, mockMetricRegistry);
        GatewayClient captureClient = gatewayClientFactory.createGatewayClient(PaymentGatewayName.SMARTPAY, GatewayOperation.CAPTURE,
                urlMap, mockSessionIdentifier, mockMetricRegistry);


        EnumMap<GatewayOperation, GatewayClient> gatewayClients = GatewayOperationClientBuilder.builder()
                .authClient(authClient)
                .captureClient(captureClient)
                .cancelClient(cancelClient)
                .refundClient(refundClient)
                .build();

        provider = new SmartpayPaymentProvider(gatewayClients, new ObjectMapper(), mockExternalRefundAvailabilityCalculator);
    }

    @Test
    public void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("smartpay"));
    }

    @Test
    public void shouldGetStatusMapper() {
        assertThat(provider.getStatusMapper(), sameInstance(SmartpayStatusMapper.get()));
    }

    @Test
    public void shouldGenerateTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(false));
    }

    @Test
    public void shouldAlwaysVerifyNotification() {
        Assert.assertThat(provider.verifyNotification(null, mock(GatewayAccountEntity.class)), is(true));
    }

    @Test
    public void shouldSendSuccessfullyAOrderForMerchant() throws Exception {

        AuthCardDetails authCardDetails = getValidTestCard();

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();

        GatewayResponse<SmartpayAuthorisationResponse> response = provider.authorise(new AuthorisationGatewayRequest(chargeEntity, authCardDetails));

        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().isPresent(), CoreMatchers.is(true));
        String transactionId = response.getBaseResponse().get().getPspReference();
        assertThat(transactionId, is(not(nullValue())));
    }

    @Test
    public void shouldRequire3dsFor3dsRequiredMerchant() throws Exception {
        AuthCardDetails authCardDetails = getValidTestCard();
        GatewayAccountEntity gatewayAccountEntity = aServiceAccount();
        gatewayAccountEntity.setRequires3ds(true);
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        mockSmartpay3dsRequiredOrderSubmitResponse();

        GatewayResponse<SmartpayAuthorisationResponse> response = provider.authorise(new AuthorisationGatewayRequest(chargeEntity, authCardDetails));

        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().isPresent(), is(true));
        SmartpayAuthorisationResponse smartpayAuthorisationResponse = response.getBaseResponse().get();
        assertThat(smartpayAuthorisationResponse.authoriseStatus(), is(REQUIRES_3DS));
        assertThat(smartpayAuthorisationResponse.getMd(), is(not(nullValue())));
        assertThat(smartpayAuthorisationResponse.getIssuerUrl(), is(not(nullValue())));
        assertThat(smartpayAuthorisationResponse.getPaRequest(), is(not(nullValue())));

    }

    @Test
    public void shouldSuccess3DSAuthorisation() {
        GatewayAccountEntity gatewayAccountEntity = aServiceAccount();
        gatewayAccountEntity.setRequires3ds(true);
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        Auth3dsDetails auth3dsDetails = AuthUtils.buildAuth3dsDetails();
        auth3dsDetails.setMd("Some smart text here");

        GatewayResponse<SmartpayAuthorisationResponse> response = provider.authorise3dsResponse(new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsDetails));

        assertTrue(response.isSuccessful());
        assertThat(response.getBaseResponse().isPresent(), is(true));
        SmartpayAuthorisationResponse smartpayAuthorisationResponse = response.getBaseResponse().get();
        assertThat(smartpayAuthorisationResponse.authoriseStatus(), is(AUTHORISED));
        assertThat(smartpayAuthorisationResponse.getPspReference(), is(not(nullValue())));
    }

    @Test
    public void shouldCaptureAPaymentSuccessfully() {

        mockSmartpaySuccessfulCaptureResponse();

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();

        GatewayResponse<WorldpayCaptureResponse> response = provider.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertTrue(response.isSuccessful());
    }

    @Test
    public void parseNotification_shouldReturnErrorIfUnparseableSoapMessage() {
        Either<String, Notifications<Pair<String, Boolean>>> response = provider.parseNotification("not valid soap message");
        assertThat(response.isLeft(), is(true));
        assertThat(response.left().value(), containsString("not valid soap message"));
    }

    @Test
    public void parseNotification_shouldReturnNotificationsIfValidSoapMessage() throws IOException {
        String originalReference = "originalReference";
        String pspReference = "pspReference";
        String merchantReference = "merchantReference";

        Either<String, Notifications<Pair<String, Boolean>>> response = provider.parseNotification(
                notificationPayloadForTransaction(originalReference, pspReference, merchantReference, "notification-capture"));

        assertThat(response.isRight(), is(true));
        ImmutableList<Notification<Pair<String, Boolean>>> notifications = response.right().value().get();

        assertThat(notifications.size(), is(1));

        Notification<Pair<String, Boolean>> smartpayNotification = notifications.get(0);

        assertThat(smartpayNotification.getTransactionId(), is(originalReference));
        assertThat(smartpayNotification.getReference(), is(pspReference));

        Pair<String, Boolean> status = smartpayNotification.getStatus();
        assertThat(status.getLeft(), is("CAPTURE"));
        assertThat(status.getRight(), is(true));
    }

    @Test
    public void shouldTreatAllNotificationsAsVerified() {
        assertThat(provider.verifyNotification(mock(Notification.class), mockGatewayAccountEntity), is(true));
    }

    @Test
    public void shouldReturnExternalRefundAvailability() {
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);
        when(mockExternalRefundAvailabilityCalculator.calculate(mockChargeEntity)).thenReturn(EXTERNAL_AVAILABLE);
        MatcherAssert.assertThat(provider.getExternalChargeRefundAvailability(mockChargeEntity), is(EXTERNAL_AVAILABLE));
    }

    private GatewayAccountEntity aServiceAccount() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("smartpay");
        gatewayAccount.setCredentials(ImmutableMap.of(
                "username", "theUsername",
                "password", "thePassword",
                "merchant_id", "theMerchantCode"
        ));
        gatewayAccount.setType(TEST);

        return gatewayAccount;
    }

    private void mockSmartpaySuccessfulOrderSubmitResponse() {
        mockSmartpayResponse(200, successAuthoriseResponse());
    }

    private void mockSmartpay3dsRequiredOrderSubmitResponse() {
        mockSmartpayResponse(200, successAuthorise3dsrequiredResponse());
    }

    private String successAuthorise3dsrequiredResponse() {
        return TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_3DS_REQUIRED_RESPONSE).replace("{{pspReference}}", "12345678");
    }

    private void mockSmartpaySuccessfulCaptureResponse() {
        mockSmartpayResponse(200, successCaptureResponse());
    }

    private void mockSmartpayResponse(int httpStatus, String responsePayload) {
        WebTarget mockTarget = mock(WebTarget.class);
        when(mockClient.target(anyString())).thenReturn(mockTarget);
        Builder mockBuilder = mock(Builder.class);
        when(mockTarget.request()).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), anyObject())).thenReturn(mockBuilder);
        when(mockSessionIdentifier.apply(Matchers.any(GatewayOrder.class), eq(mockBuilder))).thenReturn(mockBuilder);

        Response response = mock(Response.class);
        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(mockBuilder.post(Matchers.any(Entity.class))).thenReturn(response);

        when(response.getStatus()).thenReturn(httpStatus);
    }

    private String successAuthoriseResponse() {
        return TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE).replace("{{pspReference}}", "12345678");
    }

    private String successCaptureResponse() {
        return TestTemplateResourceLoader.load(SMARTPAY_CAPTURE_SUCCESS_RESPONSE).replace("{{pspReference}}", "8614440510830227");
    }

    private AuthCardDetails getValidTestCard() {
        Address address = anAddress();
        address.setLine1("123 My Street");
        address.setLine2("This road");
        address.setPostcode("SW8URR");
        address.setCity("London");
        address.setCounty("London state");
        address.setCountry("GB");

        return AuthUtils.buildAuthCardDetails("Mr. Payment", "4111111111111111", "123", "12/15", "visa", address);
    }

    private String notificationPayloadForTransaction(String originalReference, String pspReference, String merchantReference, String fileName) throws IOException {
        URL resource = getResource("templates/smartpay/" + fileName + ".json");
        return Resources.toString(resource, Charset.defaultCharset())
                .replace("{{originalReference}}", originalReference)
                .replace("{{pspReference}}", pspReference)
                .replace("{{merchantReference}}", merchantReference);
    }
}
