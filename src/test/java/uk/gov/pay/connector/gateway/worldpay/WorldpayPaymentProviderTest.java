package uk.gov.pay.connector.gateway.worldpay;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.*;
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

import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayPaymentProviderTest extends WorldpayBasePaymentProviderTest {

    @Before
    public void setup() {
        super.setup();
        mockWorldpaySuccessfulOrderSubmitResponse();
    }

    @Test
    public void shouldGetPaymentProviderName() {
        Assert.assertThat(provider.getPaymentGatewayName().getName(), is("worldpay"));
    }

    @Test
    public void shouldGenerateTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(true));
        Assert.assertThat(provider.generateTransactionId().get(), is(instanceOf(String.class)));
    }

    @Test
    public void testRefundRequestContainsReference() throws Exception {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        RefundEntity refundEntity = RefundEntityFixture.aValidRefundEntity().withCharge(chargeEntity).build();
        chargeEntity.setGatewayTransactionId("transaction-id");
        chargeEntity.setGatewayAccount(gatewayAccountEntity);

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any()))
                .thenReturn(mockGatewayClient);

        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);
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
                argThat(argument -> argument.getPayload().equals(expectedRefundRequest) && argument.getOrderRequestType().equals(OrderRequestType.REFUND)), 
                anyMap());
    }

    @Test
    public void shouldNotInclude3dsElementsWhen3dsToggleDisabled() throws Exception {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.setGatewayTransactionId("transaction-id");
        chargeEntity.setGatewayAccount(gatewayAccountEntity);

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any())).thenReturn(mockGatewayClient);

        try {
            WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);
            worldpayPaymentProvider.authorise(getCardAuthorisationRequest(chargeEntity));
        } catch (GatewayException.GatewayErrorException e) {
            ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), anyMap());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS), gatewayOrderArgumentCaptor.getValue().getPayload());
        }
    }

    @Test
    public void shouldInclude3dsElementsWhen3dsToggleEnabled() throws Exception {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity().build();
        chargeEntity.setGatewayTransactionId("transaction-id");
        chargeEntity.setGatewayAccount(gatewayAccountEntity);
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);

        when(mockChargeEntity.getGatewayAccount()).thenReturn(gatewayAccountEntity);
        when(mockChargeEntity.getExternalId()).thenReturn("uniqueSessionId");
        when(mockChargeEntity.getAmount()).thenReturn(500L);
        when(mockChargeEntity.getDescription()).thenReturn("This is a description");
        when(mockChargeEntity.getGatewayTransactionId()).thenReturn("transaction-id");

        gatewayAccountEntity.setRequires3ds(true);
        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any()))
                .thenReturn(mockGatewayClient);

        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        try {
            worldpayPaymentProvider.authorise(getCardAuthorisationRequest(mockChargeEntity));
        } catch (GatewayException.GatewayErrorException e) {
            ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), anyMap());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS), gatewayOrderArgumentCaptor.getValue().getPayload());
        }
    }
    
    @Test
    public void shouldIncludePaResponseIn3dsSecondOrder() throws Exception {
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);

        when(mockChargeEntity.getGatewayAccount()).thenReturn(gatewayAccountEntity);
        when(mockChargeEntity.getExternalId()).thenReturn("uniqueSessionId");
        when(mockChargeEntity.getGatewayTransactionId()).thenReturn("MyUniqueTransactionId!");

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 401 from gateway"));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any())).thenReturn(mockGatewayClient);

        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(mockChargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        ArgumentCaptor<List> cookies = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);

        verify(mockGatewayClient).postRequestFor(
                eq(WORLDPAY_URL), 
                eq(gatewayAccountEntity), 
                gatewayOrderArgumentCaptor.capture(), 
                cookies.capture(),
                headers.capture());

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST), gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    public void shouldIncludeProviderSessionIdWhenAvailableForCharge() throws Exception {
        String providerSessionId = "provider-session-id";
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);

        when(mockChargeEntity.getGatewayAccount()).thenReturn(gatewayAccountEntity);
        when(mockChargeEntity.getExternalId()).thenReturn("uniqueSessionId");
        when(mockChargeEntity.getGatewayTransactionId()).thenReturn("MyUniqueTransactionId!");
        when(mockChargeEntity.getProviderSessionId()).thenReturn(providerSessionId);

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any()))
                .thenReturn(mockGatewayClient);

        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(mockChargeEntity));

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
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);

        when(mockChargeEntity.getGatewayAccount()).thenReturn(gatewayAccountEntity);
        when(mockChargeEntity.getExternalId()).thenReturn("uniqueSessionId");
        when(mockChargeEntity.getGatewayTransactionId()).thenReturn("MyUniqueTransactionId!");
        when(mockChargeEntity.getProviderSessionId()).thenReturn(providerSessionId);

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));

        GatewayClientFactory gatewayClientFactory = mock(GatewayClientFactory.class);
        when(gatewayClientFactory.createGatewayClient(any(PaymentGatewayName.class), any(GatewayOperation.class), any()))
                .thenReturn(mockGatewayClient);

        WorldpayPaymentProvider worldpayPaymentProvider = new WorldpayPaymentProvider(configuration, gatewayClientFactory, environment);

        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(mockChargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        ArgumentCaptor<List<HttpCookie>> cookies = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);

        verify(mockGatewayClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                cookies.capture(),
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
    public void shouldErrorIfAuthorisationIsUnsuccessful() throws Exception {
        mockWorldpayErrorResponse(401);
        try {
            provider.authorise(getCardAuthorisationRequest());
        } catch (GatewayException.GatewayErrorException e) {
            assertEquals(e.toGatewayError(), new GatewayError("Unexpected HTTP status code 401 from gateway", ErrorType.GATEWAY_ERROR));
        }
    }

    private CardAuthorisationGatewayRequest getCardAuthorisationRequest() {
        return getCardAuthorisationRequest(aServiceAccount());
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

    private CardAuthorisationGatewayRequest getCardAuthorisationRequest(GatewayAccountEntity accountEntity) {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(accountEntity)
                .build();
        return getCardAuthorisationRequest(chargeEntity);
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
        Address address = new Address("123 My Street", "This road", "SW8URR", "London", "London state", "GB");

        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Mr. Payment")
                .withCardNo("4111111111111111")
                .withCvc("123")
                .withEndDate("12/15")
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }
}
