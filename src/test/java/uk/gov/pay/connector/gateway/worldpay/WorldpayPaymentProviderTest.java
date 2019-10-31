package uk.gov.pay.connector.gateway.worldpay;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.w3c.dom.Document;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.Auth3dsDetails;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.pay.connector.util.XPathUtils;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_FLEX_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS;
import static uk.gov.pay.connector.util.XPathUtils.getNodeListFromExpression;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayPaymentProviderTest extends WorldpayBasePaymentProviderTest {

    @Mock
    private GatewayClientFactory gatewayClientFactory;
    
    private ChargeEntityFixture chargeEntityFixture;

    @Before
    public void setup() {
        super.setup();
        mockWorldpaySuccessfulOrderSubmitResponse();
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any()))
                .thenReturn(mockGatewayClient);
        chargeEntityFixture = aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity);
    }

    @Test
    public void shouldGetPaymentProviderName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("worldpay"));
    }

    @Test
    public void shouldGenerateTransactionId() {
        assertThat(provider.generateTransactionId().isPresent(), is(true));
        assertThat(provider.generateTransactionId().get(), is(instanceOf(String.class)));
    }

    @Test
    public void testRefundRequestContainsReference() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture.withTransactionId("transaction-id").build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().withCharge(chargeEntity).build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        var worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);
        worldpayPaymentProvider.refund(RefundGatewayRequest.valueOf(refundEntity));

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
        ChargeEntity chargeEntity = chargeEntityFixture.withTransactionId("transaction-id").build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        try {
            var worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);
            worldpayPaymentProvider.authorise(getCardAuthorisationRequest(chargeEntity));
        } catch (GatewayException.GatewayErrorException e) {
            ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), anyMap());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS), 
                    gatewayOrderArgumentCaptor.getValue().getPayload());
        }
    }

    @Test
    public void shouldInclude3dsElementsWhen3dsToggleEnabled() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .build();

        gatewayAccountEntity.setRequires3ds(true);
        
        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        var worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        try {
            worldpayPaymentProvider.authorise(getCardAuthorisationRequest(chargeEntity));
        } catch (GatewayException.GatewayErrorException e) {
            ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), anyMap());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS), 
                    gatewayOrderArgumentCaptor.getValue().getPayload());
        }
    }
    
    @Test
    public void shouldIncludePaResponseIn3dsSecondOrder() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 401 from gateway"));

        var worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(chargeEntity));

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

        var worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        Auth3dsResponseGatewayRequest request = new Auth3dsResponseGatewayRequest(chargeEntity, new Auth3dsDetails());
        worldpayPaymentProvider.authorise3dsResponse(request);

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
    public void shouldNotIncludeElementsWhen3DSecureNotEnabled() throws Exception {

        var worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        GatewayClient.Response gatewayResponse = mock(GatewayClient.Response.class);
        when(gatewayResponse.getEntity()).thenReturn(TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(gatewayResponse);

        worldpayPaymentProvider.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(mockGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyMap());

        Document document = XPathUtils.getDocumentXmlString(gatewayOrderArgumentCaptor.getValue().getPayload());
        assertThat(getNodeListFromExpression(document, "/paymentService/submit/order/paymentDetails/session").getLength(),
                is(0));
        assertThat(getNodeListFromExpression(document, "/paymentService/submit/order/shopper/browser/acceptHeader").getLength(),
                is(0));
        assertThat(getNodeListFromExpression(document, "/paymentService/submit/order/shopper/browser/userAgentHeader").getLength(),
                is(0));
    }
    
    @Test
    public void shouldNotIncludeElementsWhenWorldpay3dsFlexDdcResultIsNotPresent() throws Exception {

        gatewayAccountEntity.setRequires3ds(true);
        
        var worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        GatewayClient.Response gatewayResponse = mock(GatewayClient.Response.class);
        when(gatewayResponse.getEntity()).thenReturn(TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(gatewayResponse);

        worldpayPaymentProvider.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

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
        
        var worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        GatewayClient.Response gatewayResponse = mock(GatewayClient.Response.class);
        when(gatewayResponse.getEntity()).thenReturn(TestTemplateResourceLoader.load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(gatewayResponse);
        
        AuthCardDetails authCardDetails = getValidTestCard(UUID.randomUUID().toString());
        
        worldpayPaymentProvider.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), authCardDetails));

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

        var worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(mockChargeEntity));

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
    public void shouldSendSuccessfullyAnOrderForMerchant() throws Exception {
        GatewayResponse<BaseAuthoriseResponse> response = provider.authorise(getCardAuthorisationRequest());
        assertTrue(response.isSuccessful());
        assertTrue(response.getSessionIdentifier().isPresent());
    }

    @Test
    public void shouldErrorIfWorldpayReturns401() throws Exception {
        mockWorldpayErrorResponse(401);
        try {
            provider.authorise(getCardAuthorisationRequest());
        } catch (GatewayException.GatewayErrorException e) {
            assertEquals(e.toGatewayError(), new GatewayError("Non-success HTTP status code 401 from gateway", ErrorType.GATEWAY_ERROR));
        }
    }
    
    @Test
    public void shouldErrorIfWorldpayReturns500() throws Exception {
        mockWorldpayErrorResponse(500);
        try {
            provider.authorise(getCardAuthorisationRequest());
        } catch (GatewayException.GatewayErrorException e) {
            assertEquals(e.toGatewayError(), new GatewayError("Non-success HTTP status code 500 from gateway", ErrorType.GATEWAY_ERROR));
        }
    }

    private CardAuthorisationGatewayRequest getCardAuthorisationRequest() {
        ChargeEntity chargeEntity = aValidChargeEntity().withGatewayAccountEntity(aServiceAccount()).build();
        return getCardAuthorisationRequest(chargeEntity);
    }

    private CardAuthorisationGatewayRequest getCardAuthorisationRequest(ChargeEntity chargeEntity) {
        AuthCardDetails authCardDetails = getValidTestCard();
        return new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);
    }

    private Auth3dsResponseGatewayRequest get3dsResponseGatewayRequest(ChargeEntity chargeEntity) {
        Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
        auth3dsDetails.setPaResponse("I am an opaque 3D Secure PA response from the card issuer");
        return new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsDetails);
    }

    private void mockWorldpaySuccessfulOrderSubmitResponse() {
        mockWorldpayResponse(200, successAuthoriseResponse());
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
        return getValidTestCard(null);
    }

    private AuthCardDetails getValidTestCard(String worldpay3dsFlexDdcResult) {
        Address address = new Address("123 My Street", "This road", "SW8URR", "London", "London state", "GB");
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withWorldpay3dsFlexDdcResult(worldpay3dsFlexDdcResult)
                .withCardHolder("Mr. Payment")
                .withCardNo("4111111111111111")
                .withCvc("123")
                .withEndDate("12/15")
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }
}
