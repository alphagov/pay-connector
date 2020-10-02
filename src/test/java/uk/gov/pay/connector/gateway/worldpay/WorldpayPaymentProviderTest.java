package uk.gov.pay.connector.gateway.worldpay;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.w3c.dom.Document;
import uk.gov.pay.commons.model.CardExpiryDate;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.GatewayConfig;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.pay.connector.util.XPathUtils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISED_INQUIRY_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_FLEX_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITHOUT_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITH_IP_ADDRESS;
import static uk.gov.pay.connector.util.XPathUtils.getNodeListFromExpression;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayPaymentProviderTest {

    @Mock
    private Client mockClient;
    
    @Mock
    private MetricRegistry mockMetricRegistry;
    
    @Mock
    private Histogram mockHistogram;
    
    @Mock
    private Counter mockCounter;
    
    @Mock
    private ClientFactory mockClientFactory;
    
    @Mock
    protected ConnectorConfiguration configuration;
    
    @Mock
    private GatewayConfig gatewayConfig;
    
    @Mock
    protected Environment environment;
    
    @Mock
    private GatewayClientFactory gatewayClientFactory;
    
    @Mock
    GatewayClient mockGatewayClient;

    private static final URI WORLDPAY_URL = URI.create("http://worldpay.url");
    private Map<String, String> urlMap = ImmutableMap.of(TEST.toString(), WORLDPAY_URL.toString());

    private WorldpayPaymentProvider providerWithMockedGatewayClient;
    private WorldpayPaymentProvider providerWithRealGatewayClient;

    private ChargeEntityFixture chargeEntityFixture;
    protected GatewayAccountEntity gatewayAccountEntity;
    private GatewayClient.Response authorisationSuccessResponse;

    @Before
    public void setup() {
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
        when(configuration.getGatewayConfigFor(PaymentGatewayName.WORLDPAY)).thenReturn(gatewayConfig);
        when(gatewayConfig.getUrls()).thenReturn(urlMap);
        when(environment.metrics()).thenReturn(mockMetricRegistry);

        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any()))
                .thenReturn(mockGatewayClient);
        providerWithMockedGatewayClient = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        when(mockClientFactory.createWithDropwizardClient(eq(PaymentGatewayName.WORLDPAY), any(GatewayOperation.class), any(MetricRegistry.class)))
                .thenReturn(mockClient);
        GatewayClientFactory gatewayClientFactory = new GatewayClientFactory(mockClientFactory);
        providerWithRealGatewayClient = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        gatewayAccountEntity = aServiceAccount();
        gatewayAccountEntity.setCredentials(ImmutableMap.of("merchant_id", "MERCHANTCODE"));
        chargeEntityFixture = aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity);

        authorisationSuccessResponse = mock(GatewayClient.Response.class);
        when(authorisationSuccessResponse.getEntity()).thenReturn(TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
    }

    @Test
    public void shouldGetPaymentProviderName() {
        assertThat(providerWithMockedGatewayClient.getPaymentGatewayName().getName(), is("worldpay"));
    }

    @Test
    public void shouldGenerateTransactionId() {
        assertThat(providerWithMockedGatewayClient.generateTransactionId().isPresent(), is(true));
        assertThat(providerWithMockedGatewayClient.generateTransactionId().get(), is(instanceOf(String.class)));
    }

    @Test
    public void testRefundRequestContainsReference() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture.withTransactionId("transaction-id").build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        providerWithMockedGatewayClient.refund(RefundGatewayRequest.valueOf(Charge.from(chargeEntity), refundEntity, gatewayAccountEntity));

        String expectedRefundRequest =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<!DOCTYPE paymentService PUBLIC \"-//WorldPay//DTD WorldPay PaymentService v1//EN\"\n" +
                        "        \"http://dtd.worldpay.com/paymentService_v1.dtd\">\n" +
                        "<paymentService version=\"1.4\" merchantCode=\"MERCHANTCODE\">\n" +
                        "    <modify>\n" +
                        "        <orderModification orderCode=\"transaction-id\">\n" +
                        "            <refund reference=\"" + refundEntity.getExternalId() + "\">\n" +
                        "                <amount currencyCode=\"GBP\" exponent=\"2\" value=\"500\"/>\n" +
                        "            </refund>\n" +
                        "        </orderModification>\n" +
                        "    </modify>\n" +
                        "</paymentService>\n" +
                        "";

        verify(mockGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                argThat(argument -> argument.getPayload().equals(expectedRefundRequest) &&
                        argument.getOrderRequestType().equals(OrderRequestType.REFUND)),
                anyMap());
    }

    @Test
    public void shouldNotInclude3dsElementsWhen3dsToggleDisabled() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("transaction-id")
                .build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        providerWithMockedGatewayClient.authorise(getCardAuthorisationRequest(chargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    public void shouldInclude3dsElementsWithIpAddress() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .build();

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setSendPayerIpAddressToGateway(true);

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        providerWithMockedGatewayClient.authorise(getCardAuthorisationRequest(chargeEntity, "127.0.0.1"));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITH_IP_ADDRESS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    public void shouldInclude3dsElementsWithoutIpAddress() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .build();

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setSendPayerIpAddressToGateway(false);

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        providerWithMockedGatewayClient.authorise(getCardAuthorisationRequest(chargeEntity, "127.0.0.1"));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITHOUT_IP_ADDRESS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    public void shouldIncludePaResponseIn3dsSecondOrder() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 401 from gateway"));

        providerWithMockedGatewayClient.authorise3dsResponse(get3dsResponseGatewayRequest(chargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(mockGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyList(),
                anyMap());

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    public void shouldIncludeCompletedAuthenticationInSecondOrderWhenPaRequestNotInFrontendRequest() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 401 from gateway"));

        Auth3dsResponseGatewayRequest request = new Auth3dsResponseGatewayRequest(chargeEntity, new Auth3dsResult());
        providerWithMockedGatewayClient.authorise3dsResponse(request);

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(mockGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyList(),
                anyMap());

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_3DS_FLEX_RESPONSE_AUTH_WORLDPAY_REQUEST),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    public void shouldNotIncludeElementsWhenWorldpay3dsFlexDdcResultIsNotPresent() throws Exception {

        gatewayAccountEntity.setRequires3ds(true);

        GatewayClient.Response gatewayResponse = mock(GatewayClient.Response.class);
        when(gatewayResponse.getEntity()).thenReturn(TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(gatewayResponse);

        providerWithMockedGatewayClient.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(mockGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyMap());

        Document document = XPathUtils.getDocumentXmlString(gatewayOrderArgumentCaptor.getValue().getPayload());
        XPath xPath = XPathFactory.newInstance().newXPath();
        assertThat(getNodeListFromExpression(document, "/paymentService/submit/order/additional3DSData").getLength(),
                is(0));
        assertThat(xPath.evaluate("/paymentService/submit/order/paymentDetails/session/@id", document),
                not(emptyString()));
        assertThat(xPath.evaluate("/paymentService/submit/order/shopper/browser/acceptHeader", document),
                not(emptyString()));
        assertThat(xPath.evaluate("/paymentService/submit/order/shopper/browser/userAgentHeader", document),
                not(emptyString()));
    }

    @Test
    public void shouldInclude3DS2FlexElementsWhenWorldpay3dsFlexDdcResultIsPresent() throws Exception {

        GatewayClient.Response gatewayResponse = mock(GatewayClient.Response.class);
        when(gatewayResponse.getEntity()).thenReturn(TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(gatewayResponse);

        AuthCardDetails authCardDetails = getValidTestCard(UUID.randomUUID().toString());

        providerWithMockedGatewayClient.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), authCardDetails));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(mockGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyMap());

        Document document = XPathUtils.getDocumentXmlString(gatewayOrderArgumentCaptor.getValue().getPayload());
        XPath xPath = XPathFactory.newInstance().newXPath();
        assertThat(xPath.evaluate("/paymentService/submit/order/additional3DSData/@dfReferenceId", document),
                is(authCardDetails.getWorldpay3dsFlexDdcResult().get()));
        assertThat(xPath.evaluate("/paymentService/submit/order/additional3DSData/@challengeWindowSize", document),
                is("390x400"));
        assertThat(xPath.evaluate("/paymentService/submit/order/additional3DSData/@challengePreference", document),
                is("noPreference"));
        assertThat(xPath.evaluate("/paymentService/submit/order/paymentDetails/session/@id", document),
                not(emptyString()));
        assertThat(xPath.evaluate("/paymentService/submit/order/shopper/browser/acceptHeader", document),
                not(emptyString()));
        assertThat(xPath.evaluate("/paymentService/submit/order/shopper/browser/userAgentHeader", document),
                not(emptyString()));
    }

    @Test
    public void shouldIncludeProviderSessionIdWhenAvailableForCharge() throws Exception {
        String providerSessionId = "provider-session-id";
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("MyUniqueTransactionId!")
                .withProviderSessionId(providerSessionId)
                .build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        var worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(chargeEntity));

        ArgumentCaptor<List<HttpCookie>> cookies = ArgumentCaptor.forClass(List.class);

        verify(mockGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                ArgumentCaptor.forClass(GatewayOrder.class).capture(),
                cookies.capture(),
                ArgumentCaptor.forClass(Map.class).capture());

        assertThat(cookies.getValue().size(), is(1));
        assertThat(cookies.getValue().get(0).getName(), is(WORLDPAY_MACHINE_COOKIE_NAME));
        assertThat(cookies.getValue().get(0).getValue(), is(providerSessionId));
    }

    @Test
    public void assertAuthorizationHeaderIsPassedToGatewayClient() throws Exception {
        String providerSessionId = "provider-session-id";
        ChargeEntity mockChargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("MyUniqueTransactionId!")
                .withProviderSessionId(providerSessionId)
                .build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        providerWithMockedGatewayClient.authorise3dsResponse(get3dsResponseGatewayRequest(mockChargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);

        verify(mockGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyList(),
                headers.capture());

        assertThat(headers.getValue().size(), is(1));
        assertThat(headers.getValue(), is(AuthUtil.getGatewayAccountCredentialsAsAuthHeader(gatewayAccountEntity)));
    }

    @Test
    public void shouldSuccessfullyQueryPaymentStatus() throws Exception {
        GatewayClient.Response gatewayResponse = mock(GatewayClient.Response.class);
        when(gatewayResponse.getEntity()).thenReturn(TestTemplateResourceLoader.load(WORLDPAY_AUTHORISED_INQUIRY_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture.build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(gatewayResponse);

        ChargeQueryResponse chargeQueryResponse = providerWithMockedGatewayClient.queryPaymentStatus(chargeEntity);
        
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.of(ChargeStatus.AUTHORISATION_SUCCESS)));
        assertThat(chargeQueryResponse.foundCharge(), is(true));
    }

    @Test
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        mockWorldpaySuccessfulOrderSubmitResponse();
        GatewayResponse<BaseAuthoriseResponse> response = providerWithRealGatewayClient.authorise(getCardAuthorisationRequest());
        assertTrue(response.isSuccessful());
        assertTrue(response.getSessionIdentifier().isPresent());
    }

    @Test
    public void shouldConstructGateway3DSAuthorisationResponseWithPaRequestIssuerUrlAndMachineCookieIfWorldpayAsksUsToDo3dsAgain() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture.withProviderSessionId("original-machine-cookie").build();

        GatewayClient.Response gatewayResponse = mock(GatewayClient.Response.class);
        when(gatewayResponse.getEntity()).thenReturn(TestTemplateResourceLoader.load(WORLDPAY_3DS_RESPONSE));
        when(gatewayResponse.getResponseCookies()).thenReturn(Map.of(WORLDPAY_MACHINE_COOKIE_NAME, "new-machine-cookie-value"));

        when(mockGatewayClient.postRequestFor(eq(WORLDPAY_URL), eq(chargeEntity.getGatewayAccount()), any(GatewayOrder.class),
                eq(List.of(new HttpCookie(WORLDPAY_MACHINE_COOKIE_NAME, "original-machine-cookie"))), anyMap()))
                .thenReturn(gatewayResponse);

        var auth3dsResult = new Auth3dsResult();
        auth3dsResult.setPaResponse("pa-response");
        var auth3dsResponseGatewayRequest = new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsResult);

        Gateway3DSAuthorisationResponse result = providerWithMockedGatewayClient.authorise3dsResponse(auth3dsResponseGatewayRequest);

        assertThat(result.getGateway3dsRequiredParams().isPresent(), is(true));
        assertThat(result.getGateway3dsRequiredParams().get().toAuth3dsRequiredEntity().getPaRequest(), is("eJxVUsFuwjAM/ZWK80aSUgpFJogNpHEo2hjTzl"));
        assertThat(result.getGateway3dsRequiredParams().get().toAuth3dsRequiredEntity().getIssuerUrl(),
                is("https://secure-test.worldpay.com/jsp/test/shopper/ThreeDResponseSimulator.jsp"));

        assertThat(result.getProviderSessionIdentifier().isPresent(), is (true));
        assertThat(result.getProviderSessionIdentifier().get(), is (ProviderSessionIdentifier.of("new-machine-cookie-value")));
    }

    @Test
    public void shouldErrorIfWorldpayReturns401() throws Exception {
        mockWorldpayErrorResponse(401);
        try {
            providerWithRealGatewayClient.authorise(getCardAuthorisationRequest());
        } catch (GatewayException.GatewayErrorException e) {
            assertGatewayErrorEquals(e.toGatewayError(), new GatewayError("Non-success HTTP status code 401 from gateway", ErrorType.GATEWAY_ERROR));
        }
    }

    @Test
    public void shouldErrorIfWorldpayReturns500() throws Exception {
        mockWorldpayErrorResponse(500);
        try {
            providerWithRealGatewayClient.authorise(getCardAuthorisationRequest());
        } catch (GatewayException.GatewayErrorException e) {
            assertGatewayErrorEquals(e.toGatewayError(), new GatewayError("Non-success HTTP status code 500 from gateway", ErrorType.GATEWAY_ERROR));
        }
    }

    private CardAuthorisationGatewayRequest getCardAuthorisationRequest() {
        return getCardAuthorisationRequest(chargeEntityFixture.build());
    }

    private CardAuthorisationGatewayRequest getCardAuthorisationRequest(ChargeEntity chargeEntity) {
        return getCardAuthorisationRequest(chargeEntity, null);
    }

    private CardAuthorisationGatewayRequest getCardAuthorisationRequest(ChargeEntity chargeEntity, String ipAddress) {
        AuthCardDetails authCardDetails = getValidTestCard();
        authCardDetails.setIpAddress(ipAddress);
        return new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);
    }

    private Auth3dsResponseGatewayRequest get3dsResponseGatewayRequest(ChargeEntity chargeEntity) {
        Auth3dsResult auth3dsResult = new Auth3dsResult();
        auth3dsResult.setPaResponse("I am an opaque 3D Secure PA response from the card issuer");
        return new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsResult);
    }

    private void mockWorldpaySuccessfulOrderSubmitResponse() {
        String successAuthoriseResponse = TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        mockWorldpayResponse(200, successAuthoriseResponse);
    }

    private AuthCardDetails getValidTestCard() {
        return getValidTestCard(null);
    }

    private AuthCardDetails getValidTestCard(String worldpay3dsFlexDdcResult) {
        Address address = new Address("123 My Street", "This road", "SW8URR", "London", "London state", "GB");
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withWorldpay3dsFlexDdcResult(worldpay3dsFlexDdcResult)
                .withCardHolder("Mr. Payment")
                .withCardNo("4111111111111111")
                .withCvc("123")
                .withEndDate(CardExpiryDate.valueOf("12/15"))
                .withCardBrand("visa")
                .withAddress(address)
                .build();
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

    private void assertGatewayErrorEquals(GatewayError actual, GatewayError expected) {
        assertNotNull(actual);
        assertThat(actual.getMessage(), is(expected.getMessage()));
        assertThat(actual.getErrorType(), is(expected.getErrorType()));
    }

    private void mockWorldpayErrorResponse(int httpStatus) {
        String errorResponse = TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE);
        mockWorldpayResponse(httpStatus, errorResponse);
    }

    private void mockWorldpayResponse(int httpStatus, String responsePayload) {
        WebTarget mockTarget = mock(WebTarget.class);
        when(mockClient.target(any(URI.class))).thenReturn(mockTarget);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        when(mockTarget.request()).thenReturn(mockBuilder);
        when(mockBuilder.header(anyString(), anyString())).thenReturn(mockBuilder);

        Map<String, NewCookie> responseCookies =
                Collections.singletonMap(WORLDPAY_MACHINE_COOKIE_NAME, NewCookie.valueOf("value-from-worldpay"));

        Response response = mock(Response.class);
        when(response.readEntity(String.class)).thenReturn(responsePayload);
        when(mockBuilder.post(any(Entity.class))).thenReturn(response);
        when(response.getCookies()).thenReturn(responseCookies);

        when(response.getStatus()).thenReturn(httpStatus);
    }
}
