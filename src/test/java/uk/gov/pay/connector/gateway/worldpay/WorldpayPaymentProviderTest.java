package uk.gov.pay.connector.gateway.worldpay;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import io.dropwizard.setup.Environment;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.GatewayOperationClientBuilder;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.usernotification.model.Notification;
import uk.gov.pay.connector.usernotification.model.Notifications;
import uk.gov.pay.connector.util.AuthUtils;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;

import static fj.data.Either.left;
import static java.lang.String.format;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.ErrorType.UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY;
import static uk.gov.pay.connector.gateway.model.GatewayError.unexpectedStatusCodeFromGateway;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_NOTIFICATION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayPaymentProviderTest {

    private WorldpayPaymentProvider provider;
    private Map<String, String> urlMap = ImmutableMap.of(TEST.toString(), "http://worldpay.url");

    EnumMap<GatewayOperation, GatewayClient> gatewayClients;

    @Mock
    private Client mockClient;
    @Mock
    private GatewayClient mockGatewayClient;
    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;
    @Mock
    private MetricRegistry mockMetricRegistry;
    @Mock
    private Histogram mockHistogram;
    @Mock
    private Counter mockCounter;
    @Mock
    private ClientFactory mockClientFactory;
    @Mock
    private ConnectorConfiguration configuration;
    @Mock
    private GatewayConfig gatewayConfig;
    @Mock
    private Environment environment;

    @Before
    public void setup() {
        GatewayClientFactory gatewayClientFactory = new GatewayClientFactory(mockClientFactory);
        mockWorldpaySuccessfulOrderSubmitResponse();
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(mockClientFactory.createWithDropwizardClient(
                eq(PaymentGatewayName.WORLDPAY), any(GatewayOperation.class), any(MetricRegistry.class))
        )
                .thenReturn(mockClient);
        
        when(configuration.getGatewayConfigFor(PaymentGatewayName.WORLDPAY)).thenReturn(gatewayConfig);
        when(gatewayConfig.getUrls()).thenReturn(urlMap);
        when(configuration.getWorldpayConfig()).thenReturn(mock(WorldpayConfig.class));
        when(environment.metrics()).thenReturn(mockMetricRegistry);

        provider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);
    }

    @Test
    public void shouldGetPaymentProviderName() {
        Assert.assertThat(provider.getPaymentGatewayName().getName(), is("worldpay"));
    }

    @Test
    public void shouldGetStatusMapper() {
        Assert.assertThat(provider.getStatusMapper(), sameInstance(WorldpayStatusMapper.get()));
    }

    @Test
    public void shouldGenerateTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(true));
        Assert.assertThat(provider.generateTransactionId().get(), is(instanceOf(String.class)));
    }

    @Test
    public void shouldAlwaysVerifyNotification() {
        Assert.assertThat(provider.verifyNotification(null, mock(GatewayAccountEntity.class)), is(true));
    }

    @Test
    public void testRefundRequestContainsReference() {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().withCharge(chargeEntity).build();
        chargeEntity.setGatewayTransactionId("transaction-id");
        chargeEntity.setGatewayAccount(mockGatewayAccountEntity);

        Map<String, String> credentialsMap = ImmutableMap.of("merchant_id", "MERCHANTCODE");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialsMap);
        when(mockGatewayClient.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class)))
                .thenReturn(left(unexpectedStatusCodeFromGateway("Unexpected HTTP status code 400 from gateway")));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any(Map.class), any(BiFunction.class), any())).thenReturn(mockGatewayClient);
        
        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);
        worldpayPaymentProvider.refund(RefundGatewayRequest.valueOf(refundEntity));

        String expectedRefundRequest =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <modify>\n" +
                "        <orderModification orderCode=\"transaction-id\">\n" +
                "            <refund reference=\"" +refundEntity.getExternalId()+ "\">\n" +
                "                <amount currencyCode=\"GBP\" exponent=\"2\" value=\"500\"/>\n" +
                "            </refund>\n" +
                "        </orderModification>\n" +
                "    </modify>\n" +
                "</paymentService>\n" +
                "";

        verify(mockGatewayClient).postRequestFor(eq(null), eq(mockGatewayAccountEntity), argThat(argument -> argument.getPayload().equals(expectedRefundRequest) && argument.getOrderRequestType().equals(OrderRequestType.REFUND)));
    }

    @Test
    public void shouldNotInclude3dsElementsWhen3dsToggleDisabled() throws Exception {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.setGatewayTransactionId("transaction-id");
        chargeEntity.setGatewayAccount(mockGatewayAccountEntity);

        Map<String, String> credentialsMap = ImmutableMap.of("merchant_id", "MERCHANTCODE");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialsMap);
        when(mockGatewayAccountEntity.isRequires3ds()).thenReturn(false);
        when(mockGatewayClient.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class)))
                .thenReturn(left(unexpectedStatusCodeFromGateway("Unexpected HTTP status code 400 from gateway")));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any(Map.class), any(BiFunction.class), any())).thenReturn(mockGatewayClient);

        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        worldpayPaymentProvider.authorise(getCardAuthorisationRequest(chargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(mockGatewayClient).postRequestFor(eq(null), eq(mockGatewayAccountEntity), gatewayOrderArgumentCaptor.capture());

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS), gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    public void shouldInclude3dsElementsWhen3dsToggleEnabled() throws Exception {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.setGatewayTransactionId("transaction-id");

        EnumMap<GatewayOperation, GatewayClient> gatewayClientEnumMap = GatewayOperationClientBuilder.builder()
                .authClient(mockGatewayClient)
                .captureClient(mockGatewayClient)
                .cancelClient(mockGatewayClient)
                .refundClient(mockGatewayClient)
                .build();

        chargeEntity.setGatewayAccount(mockGatewayAccountEntity);
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);

        when(mockChargeEntity.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
        when(mockChargeEntity.getExternalId()).thenReturn("uniqueSessionId");
        when(mockChargeEntity.getAmount()).thenReturn(500L);
        when(mockChargeEntity.getDescription()).thenReturn("This is a description");
        when(mockChargeEntity.getGatewayTransactionId()).thenReturn("transaction-id");

        Map<String, String> credentialsMap = ImmutableMap.of("merchant_id", "MERCHANTCODE");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialsMap);
        when(mockGatewayAccountEntity.isRequires3ds()).thenReturn(true);
        when(mockGatewayClient.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class)))
                .thenReturn(left(unexpectedStatusCodeFromGateway("Unexpected HTTP status code 400 from gateway")));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any(Map.class), any(BiFunction.class), any())).thenReturn(mockGatewayClient);

        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        worldpayPaymentProvider.authorise(getCardAuthorisationRequest(mockChargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(mockGatewayClient).postRequestFor(eq(null), eq(mockGatewayAccountEntity), gatewayOrderArgumentCaptor.capture());

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS), gatewayOrderArgumentCaptor.getValue().getPayload());
    }


    @Test
    public void shouldIncludePaResponseIn3dsSecondOrder() throws Exception {
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);

        when(mockChargeEntity.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
        when(mockChargeEntity.getExternalId()).thenReturn("uniqueSessionId");
        when(mockChargeEntity.getGatewayTransactionId()).thenReturn("MyUniqueTransactionId!");

        Map<String, String> credentialsMap = ImmutableMap.of("merchant_id", "MERCHANTCODE");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialsMap);
        when(mockGatewayClient.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class)))
                .thenReturn(left(unexpectedStatusCodeFromGateway("Unexpected HTTP status code 401 from gateway")));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any(Map.class), any(BiFunction.class), any())).thenReturn(mockGatewayClient);
        
        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(mockChargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(mockGatewayClient).postRequestFor(eq(null), eq(mockGatewayAccountEntity), gatewayOrderArgumentCaptor.capture());

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST), gatewayOrderArgumentCaptor.getValue().getPayload());
    }


    @Test
    public void shouldIncludeProviderSessionIdWhenAvailableForCharge() {
        String providerSessionId = "provider-session-id";
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);

        EnumMap<GatewayOperation, GatewayClient> gatewayClientEnumMap = GatewayOperationClientBuilder.builder()
                .authClient(mockGatewayClient)
                .captureClient(mockGatewayClient)
                .cancelClient(mockGatewayClient)
                .refundClient(mockGatewayClient)
                .build();

        when(mockChargeEntity.getGatewayAccount()).thenReturn(mockGatewayAccountEntity);
        when(mockChargeEntity.getExternalId()).thenReturn("uniqueSessionId");
        when(mockChargeEntity.getGatewayTransactionId()).thenReturn("MyUniqueTransactionId!");
        when(mockChargeEntity.getProviderSessionId()).thenReturn(providerSessionId);

        Map<String, String> credentialsMap = ImmutableMap.of("merchant_id", "MERCHANTCODE");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialsMap);
        when(mockGatewayClient.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class)))
                .thenReturn(left(unexpectedStatusCodeFromGateway("Unexpected HTTP status code 400 from gateway")));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any(Map.class), any(BiFunction.class), any())).thenReturn(mockGatewayClient);
        
        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(mockChargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(mockGatewayClient).postRequestFor(eq(null), eq(mockGatewayAccountEntity), gatewayOrderArgumentCaptor.capture());

        assertTrue(gatewayOrderArgumentCaptor.getValue().getProviderSessionId().isPresent());
        assertThat(gatewayOrderArgumentCaptor.getValue().getProviderSessionId().get(), is(providerSessionId));
    }

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        GatewayResponse<WorldpayOrderStatusResponse> response = provider.authorise(getCardAuthorisationRequest());
        assertTrue(response.isSuccessful());
        assertTrue(response.getSessionIdentifier().isPresent());
    }

    @Test
    public void shouldCaptureAPaymentSuccessfully() throws Exception {
        mockWorldpaySuccessfulCaptureResponse();

        GatewayResponse response = provider.capture(getCaptureRequest());
        assertTrue(response.isSuccessful());
    }

    @Test
    public void shouldErrorIfAuthorisationIsUnsuccessful() {
        mockWorldpayErrorResponse(401);
        GatewayResponse<WorldpayOrderStatusResponse> response = provider.authorise(getCardAuthorisationRequest());

        assertThat(response.isFailed(), is(true));
        assertFalse(response.getSessionIdentifier().isPresent());
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected HTTP status code 401 from gateway",
                UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }

    @Test
    public void shouldErrorIfOrderReferenceNotKnownInCapture() {
        mockWorldpayErrorResponse(200);
        GatewayResponse<WorldpayCaptureResponse> response = provider.capture(getCaptureRequest());

        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Worldpay capture response (error code: 5, error: Order has already been paid)", GENERIC_GATEWAY_ERROR));
    }

    @Test
    public void shouldErrorIfWorldpayResponseIsNot200() {
        mockWorldpayErrorResponse(400);
        GatewayResponse<WorldpayCaptureResponse> response = provider.capture(getCaptureRequest());
        assertThat(response.isFailed(), is(true));
        assertThat(response.getGatewayError().isPresent(), is(true));
        assertEquals(response.getGatewayError().get(), new GatewayError("Unexpected HTTP status code 400 from gateway",
                UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY));
    }

    @Test
    public void parseNotification_shouldReturnErrorIfUnparseableXml() {
        Either<String, Notifications<String>> response = provider.parseNotification("not valid xml");
        assertThat(response.isLeft(), is(true));
        assertThat(response.left().value(), startsWith("javax.xml.bind.UnmarshalException"));
    }

    @Test
    public void parseNotification_shouldReturnNotificationsIfValidXml() throws IOException {
        String transactionId = "transaction-id";
        String referenceId = "reference-id";
        String status = "CHARGED";
        String bookingDateDay = "10";
        String bookingDateMonth = "03";
        String bookingDateYear = "2017";
        Either<String, Notifications<String>> response = provider.parseNotification(notificationPayloadForTransaction(transactionId, referenceId, status, bookingDateDay, bookingDateMonth, bookingDateYear));
        assertThat(response.isRight(), is(true));

        ImmutableList<Notification<String>> notifications = response.right().value().get();

        assertThat(notifications.size(), is(1));

        Notification<String> worldpayNotification = notifications.get(0);

        assertThat(worldpayNotification.getTransactionId(), is(transactionId));
        assertThat(worldpayNotification.getReference(), is(referenceId));
        assertThat(worldpayNotification.getStatus(), is(status));
        assertThat(worldpayNotification.getGatewayEventDate(), is(ZonedDateTime.parse(format("%s-%s-%sT00:00Z", bookingDateYear, bookingDateMonth, bookingDateDay))));
    }

    @Test
    public void shouldTreatAllNotificationsAsVerified() {
        assertThat(provider.verifyNotification(mock(Notification.class), mockGatewayAccountEntity), is(true));
    }

    private String notificationPayloadForTransaction(
            String transactionId,
            String referenceId,
            String status,
            String bookingDateDay,
            String bookingDateMonth,
            String bookingDateYear) throws IOException {
        return TestTemplateResourceLoader.load(WORLDPAY_NOTIFICATION)
                .replace("{{transactionId}}", transactionId)
                .replace("{{refund-ref}}", referenceId)
                .replace("{{status}}", status)
                .replace("{{bookingDateDay}}", bookingDateDay)
                .replace("{{bookingDateMonth}}", bookingDateMonth)
                .replace("{{bookingDateYear}}", bookingDateYear);
    }

    private AuthorisationGatewayRequest getCardAuthorisationRequest() {
        return getCardAuthorisationRequest(aServiceAccount());
    }

    private AuthorisationGatewayRequest getCardAuthorisationRequest(ChargeEntity chargeEntity) {
        AuthCardDetails authCardDetails = getValidTestCard();
        return new AuthorisationGatewayRequest(chargeEntity, authCardDetails);
    }

    private Auth3dsResponseGatewayRequest get3dsResponseGatewayRequest(ChargeEntity chargeEntity) {
        Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
        auth3dsDetails.setPaResponse("I am an opaque 3D Secure PA response from the card issuer");
        return new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsDetails);
    }

    private AuthorisationGatewayRequest getCardAuthorisationRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(accountEntity)
                .build();
        return getCardAuthorisationRequest(chargeEntity);
    }


    private GatewayAccountEntity aServiceAccount() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("worldpay");
        gatewayAccount.setRequires3ds(false);
        gatewayAccount.setCredentials(ImmutableMap.of(
                CREDENTIALS_MERCHANT_ID, "worlpay-merchant",
                CREDENTIALS_USERNAME, "worldpay-password",
                CREDENTIALS_PASSWORD, "password"
        ));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }

    private void assertEquals(GatewayError actual, GatewayError expected) {
        assertNotNull(actual);
        assertThat(actual.getMessage(), is(expected.getMessage()));
        assertThat(actual.getErrorType(), is(expected.getErrorType()));
    }

    private CaptureGatewayRequest getCaptureRequest() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(aServiceAccount())
                .build();
        return CaptureGatewayRequest.valueOf(chargeEntity);
    }

    private void mockWorldpayErrorResponse(int httpStatus) {
        mockWorldpayResponse(httpStatus, errorResponse());
    }

    private void mockWorldpaySuccessfulCaptureResponse() {
        mockWorldpayResponse(200, successCaptureResponse());
    }

    private void mockWorldpaySuccessfulOrderSubmitResponse() {
        mockWorldpayResponse(200, successAuthoriseResponse());
    }

    private void mockWorldpayResponse(int httpStatus, String responsePayload) {
        WebTarget mockTarget = mock(WebTarget.class);
        when(mockClient.target(anyString())).thenReturn(mockTarget);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        when(mockTarget.request()).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), any(Object.class))).thenReturn(mockBuilder);

        Map<String, NewCookie> responseCookies =
                Collections.singletonMap(WORLDPAY_MACHINE_COOKIE_NAME, NewCookie.valueOf("value-from-worldpay"));

        Response response = mock(Response.class);
        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(mockBuilder.post(any(Entity.class))).thenReturn(response);
        when(response.getCookies()).thenReturn(responseCookies);

        when(response.getStatus()).thenReturn(httpStatus);
    }

    private String successCaptureResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <reply>\n" +
                "        <ok>\n" +
                "            <captureReceived orderCode=\"MyUniqueTransactionId!\">\n" +
                "                <amount value=\"500\" currencyCode=\"GBP\" exponent=\"2\" debitCreditIndicator=\"credit\"/>\n" +
                "            </captureReceived>\n" +
                "        </ok>\n" +
                "    </reply>\n" +
                "</paymentService>";
    }

    private String errorResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <reply>\n" +
                "        <error code=\"5\">\n" +
                "            <![CDATA[Order has already been paid]]>\n" +
                "        </error>\n" +
                "    </reply>\n" +
                "</paymentService>";
    }

    private String successAuthoriseResponse() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                "    <reply>\n" +
                "        <orderStatus orderCode=\"MyUniqueTransactionId!22233\">\n" +
                "            <payment>\n" +
                "                <paymentMethod>VISA-SSL</paymentMethod>\n" +
                "                <paymentMethodDetail>\n" +
                "                    <card number=\"4444********1111\" type=\"creditcard\">\n" +
                "                        <expiryDate>\n" +
                "                            <date month=\"11\" year=\"2099\"/>\n" +
                "                        </expiryDate>\n" +
                "                    </card>\n" +
                "                </paymentMethodDetail>\n" +
                "                <amount value=\"500\" currencyCode=\"GBP\" exponent=\"2\" debitCreditIndicator=\"credit\"/>\n" +
                "                <lastEvent>AUTHORISED</lastEvent>\n" +
                "                <AuthorisationId id=\"666\"/>\n" +
                "                <CVCResultCode description=\"NOT SENT TO ACQUIRER\"/>\n" +
                "                <AVSResultCode description=\"NOT SENT TO ACQUIRER\"/>\n" +
                "                <cardHolderName>\n" +
                "                    <![CDATA[Coucou]]>\n" +
                "                </cardHolderName>\n" +
                "                <issuerCountryCode>N/A</issuerCountryCode>\n" +
                "                <balance accountType=\"IN_PROCESS_AUTHORISED\">\n" +
                "                    <amount value=\"500\" currencyCode=\"GBP\" exponent=\"2\" debitCreditIndicator=\"credit\"/>\n" +
                "                </balance>\n" +
                "                <riskScore value=\"51\"/>\n" +
                "            </payment>\n" +
                "        </orderStatus>\n" +
                "    </reply>\n" +
                "</paymentService>";
    }

    private AuthCardDetails getValidTestCard() {
        Address address = new Address("123 My Street", "This road", "SW8URR", "London", "London state", "GB");

        return AuthUtils.buildAuthCardDetails("Mr. Payment", "4111111111111111", "123", "12/15", "visa", address);
    }
}
