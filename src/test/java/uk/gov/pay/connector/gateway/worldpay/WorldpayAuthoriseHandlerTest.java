package uk.gov.pay.connector.gateway.worldpay;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import uk.gov.pay.commons.model.CardExpiryDate;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.ClientFactory;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayClientFactory;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.XPathUtils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITHOUT_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITH_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;
import static uk.gov.pay.connector.util.XPathUtils.getNodeListFromExpression;

@ExtendWith(MockitoExtension.class)
class WorldpayAuthoriseHandlerTest {

    private final URI WORLDPAY_URL = URI.create("http://worldpay.url");
    
    private final Map<String, URI> GATEWAY_URL_MAP = Map.of(TEST.toString(), WORLDPAY_URL);
    
    private WorldpayAuthoriseHandler worldpayAuthoriseHandler;
    
    @Mock private GatewayClient authoriseClient;

    @Mock private GatewayClient.Response authorisationSuccessResponse;

    private ChargeEntityFixture chargeEntityFixture;
    
    private GatewayAccountEntity gatewayAccountEntity;
    
    @BeforeEach
    void setup() {
        worldpayAuthoriseHandler = new WorldpayAuthoriseHandler(authoriseClient, GATEWAY_URL_MAP);

        gatewayAccountEntity = aServiceAccount();
        gatewayAccountEntity.setCredentials( Map.of("merchant_id", "MERCHANTCODE"));
        chargeEntityFixture = aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity);
    }

    @Test
    void should_not_include_3ds_elements_when_3ds_toggle_disabled() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("transaction-id")
                .build();

        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authorise(getCardAuthorisationRequest(chargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_include_3ds_elements_with_ip_address() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .build();

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setSendPayerIpAddressToGateway(true);

        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authorise(getCardAuthorisationRequest(chargeEntity, "127.0.0.1"));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITH_IP_ADDRESS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_include_3ds_elements_without_ip_address() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .build();

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setSendPayerIpAddressToGateway(false);

        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authorise(getCardAuthorisationRequest(chargeEntity, "127.0.0.1"));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITHOUT_IP_ADDRESS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_not_include_exemption_element_if_account_has_no_worldpay_3ds_flex_credentials() throws Exception {

        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setIntegrationVersion3ds(1);
        gatewayAccountEntity.setWorldpay3dsFlexCredentialsEntity(null);
        chargeEntityFixture.withGatewayAccountEntity(gatewayAccountEntity);
        worldpayAuthoriseHandler.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

        verifyNoExemptionRequestInAuthorisationRequest();
    }

    @Test
    void should_not_include_exemption_element_if_account_has_exemption_engine_set_to_false() throws Exception {

        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setIntegrationVersion3ds(1);
        gatewayAccountEntity.setWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().build());
        chargeEntityFixture.withGatewayAccountEntity(gatewayAccountEntity);
        worldpayAuthoriseHandler.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

        verifyNoExemptionRequestInAuthorisationRequest();
    }

    @Test
    void should_include_exemption_element_if_account_has_exemption_engine_set_to_true() throws Exception {

        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setIntegrationVersion3ds(1);
        gatewayAccountEntity.setWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build());
        chargeEntityFixture.withGatewayAccountEntity(gatewayAccountEntity);
        worldpayAuthoriseHandler.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(authoriseClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyMap());

        Document document = XPathUtils.getDocumentXmlString(gatewayOrderArgumentCaptor.getValue().getPayload());
        XPath xPath = XPathFactory.newInstance().newXPath();
        assertThat(xPath.evaluate("/paymentService/submit/order/exemption/@type", document), is("OP"));
        assertThat(xPath.evaluate("/paymentService/submit/order/exemption/@placement", document), is("AUTHORISATION"));
    }

    @Test
    void should_not_include_exemption_element_if_account_has_exemption_engine_set_to_true_but_3ds_is_not_enabled() throws Exception {

        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        gatewayAccountEntity.setRequires3ds(false);
        gatewayAccountEntity.setIntegrationVersion3ds(1);
        gatewayAccountEntity.setWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build());
        chargeEntityFixture.withGatewayAccountEntity(gatewayAccountEntity);
        worldpayAuthoriseHandler.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

        verifyNoExemptionRequestInAuthorisationRequest();
    }

    private void verifyNoExemptionRequestInAuthorisationRequest() throws Exception {
        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(authoriseClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyMap());

        Document document = XPathUtils.getDocumentXmlString(gatewayOrderArgumentCaptor.getValue().getPayload());
        assertThat(getNodeListFromExpression(document, "/paymentService/submit/order/exemption").getLength(),
                is(0));
    }

    @Test
    void should_not_include_elements_when_worldpay_3ds_flex_ddc_result_is_not_present() throws Exception {

        gatewayAccountEntity.setRequires3ds(true);

        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(authoriseClient).postRequestFor(
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
    void should_include_3DS2_flex_elements_when_worldpay_3ds_flex_ddc_result_is_present() throws Exception {

        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        AuthCardDetails authCardDetails = getValidTestCard(UUID.randomUUID().toString());

        worldpayAuthoriseHandler.authorise(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), authCardDetails));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(authoriseClient).postRequestFor(
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
    void should_send_successfully_an_order_for_merchant() {
        Client mockClient = mockWorldpaySuccessfulOrderSubmitResponse();

        var handlerWithRealJerseyClient = new WorldpayAuthoriseHandler(createGatewayClient(mockClient), GATEWAY_URL_MAP);
        
        GatewayResponse response = handlerWithRealJerseyClient.authorise(getCardAuthorisationRequest(chargeEntityFixture.build()));
        assertTrue(response.isSuccessful());
        assertTrue(response.getSessionIdentifier().isPresent());
    }

    private GatewayClient createGatewayClient(Client mockClient) {
        ClientFactory mockClientFactory = mock(ClientFactory.class);
        GatewayClientFactory gatewayClientFactory = new GatewayClientFactory(mockClientFactory);
        when(mockClientFactory.createWithDropwizardClient(eq(PaymentGatewayName.WORLDPAY), any(GatewayOperation.class), any(MetricRegistry.class)))
                .thenReturn(mockClient);
        MetricRegistry mockMetricRegistry = mock(MetricRegistry.class);
        Histogram mockHistogram = mock(Histogram.class);
        when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        return gatewayClientFactory.createGatewayClient(WORLDPAY, AUTHORISE, mockMetricRegistry);
    }

    private Client mockWorldpaySuccessfulOrderSubmitResponse() {
        String successAuthoriseResponse = load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        return mockWorldpayResponse(200, successAuthoriseResponse);
    }

    private Client mockWorldpayResponse(int httpStatus, String responsePayload) {
        WebTarget mockTarget = mock(WebTarget.class);
        Client mockClient = mock(Client.class);
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
        
        return mockClient;
    }
    
    private GatewayAccountEntity aServiceAccount() {
        GatewayAccountEntity gatewayAccount = new GatewayAccountEntity();
        gatewayAccount.setId(1L);
        gatewayAccount.setGatewayName("worldpay");
        gatewayAccount.setRequires3ds(false);
        gatewayAccount.setCredentials(Map.of(
                CREDENTIALS_MERCHANT_ID, "worlpay-merchant",
                CREDENTIALS_USERNAME, "worldpay-password",
                CREDENTIALS_PASSWORD, "password"
        ));
        gatewayAccount.setType(TEST);
        return gatewayAccount;
    }

    private CardAuthorisationGatewayRequest getCardAuthorisationRequest(ChargeEntity chargeEntity) {
        return getCardAuthorisationRequest(chargeEntity, null);
    }

    private CardAuthorisationGatewayRequest getCardAuthorisationRequest(ChargeEntity chargeEntity, String ipAddress) {
        AuthCardDetails authCardDetails = getValidTestCard();
        authCardDetails.setIpAddress(ipAddress);
        return new CardAuthorisationGatewayRequest(chargeEntity, authCardDetails);
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
}
