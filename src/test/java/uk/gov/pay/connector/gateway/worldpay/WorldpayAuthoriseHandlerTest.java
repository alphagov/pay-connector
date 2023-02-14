package uk.gov.pay.connector.gateway.worldpay;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.w3c.dom.Document;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
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
import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.AcceptLanguageHeaderParser;
import uk.gov.pay.connector.util.XPathUtils;
import uk.gov.service.payments.commons.model.CardExpiryDate;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntityFixture.anAgreementEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.GatewayOperation.AUTHORISE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_REQUEST_3DS_FLEX_NON_JS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS_WITH_EMAIL;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITHOUT_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITH_EMAIL;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITH_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_SETUP_AGREEMENT;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_WITH_REFERENCE_IN_DESCRIPTION;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;
import static uk.gov.pay.connector.util.XPathUtils.getNodeListFromExpression;

@ExtendWith(MockitoExtension.class)
class WorldpayAuthoriseHandlerTest {

    private final URI WORLDPAY_URL = URI.create("http://worldpay.url");
    private final Map<String, URI> GATEWAY_URL_MAP = Map.of(TEST.toString(), WORLDPAY_URL);
    
    @Mock private GatewayClient authoriseClient;
    @Mock private GatewayClient.Response authorisationSuccessResponse;

    private ChargeEntityFixture chargeEntityFixture;
    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountCredentialsEntity creds;
    private WorldpayAuthoriseHandler worldpayAuthoriseHandler;
    
    @BeforeEach
    void setup() {
        worldpayAuthoriseHandler = new WorldpayAuthoriseHandler(authoriseClient, GATEWAY_URL_MAP, new AcceptLanguageHeaderParser());

        gatewayAccountEntity = aServiceAccount();
        creds = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of(
                        CREDENTIALS_MERCHANT_ID, "MERCHANTCODE",
                        CREDENTIALS_USERNAME, "worldpay-password",
                        CREDENTIALS_PASSWORD, "password"))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(WORLDPAY.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(creds));
        chargeEntityFixture = aValidChargeEntity()
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withPaymentProvider("worldpay")
                        .withCredentials(Map.of(
                                CREDENTIALS_MERCHANT_ID, "MERCHANTCODE",
                                CREDENTIALS_USERNAME, "worldpay-password",
                                CREDENTIALS_PASSWORD, "password"
                        ))
                        .build())
                .withGatewayAccountEntity(gatewayAccountEntity);
    }

    @Test
    void should_not_include_3ds_elements_when_3ds_toggle_disabled() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("transaction-id")
                .build();

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"),  any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption(getCardAuthorisationRequest(chargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());
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

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption(getCardAuthorisationRequest(chargeEntity, "127.0.0.1"));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());
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

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption(getCardAuthorisationRequest(chargeEntity, "127.0.0.1"));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITHOUT_IP_ADDRESS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }
    
    @Test
    void should_include_exemption_element_if_account_has_exemption_engine_set_to_true() throws Exception {

        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        gatewayAccountEntity.setRequires3ds(true);
        chargeEntityFixture.withGatewayAccountEntity(gatewayAccountEntity);
        worldpayAuthoriseHandler.authoriseWithExemption(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(authoriseClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(WORLDPAY), eq("test"),
                gatewayOrderArgumentCaptor.capture(),
                anyMap());

        Document document = XPathUtils.getDocumentXmlString(gatewayOrderArgumentCaptor.getValue().getPayload());
        XPath xPath = XPathFactory.newInstance().newXPath();
        assertThat(xPath.evaluate("/paymentService/submit/order/exemption/@type", document), is("OP"));
        assertThat(xPath.evaluate("/paymentService/submit/order/exemption/@placement", document), is("OPTIMISED"));
    }

    @Test
    void should_not_include_exemption_element() throws Exception {

        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));
        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setIntegrationVersion3ds(1);
        gatewayAccountEntity.setWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build());
        chargeEntityFixture.withGatewayAccountEntity(gatewayAccountEntity);
        
        worldpayAuthoriseHandler.authoriseWithoutExemption(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(authoriseClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(WORLDPAY), eq("test"),
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
        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), getValidTestCard()));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(authoriseClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(WORLDPAY), eq("test"),
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
        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        AuthCardDetails authCardDetails = getValidTestCard(UUID.randomUUID().toString());

        worldpayAuthoriseHandler.authoriseWithoutExemption(new CardAuthorisationGatewayRequest(chargeEntityFixture.build(), authCardDetails));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(authoriseClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(WORLDPAY), eq("test"),
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
    void should_include_email_when_present_and_send_email_to_gateway_enabled_and_3ds_disabled() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .withEmail("test@email.invalid")
                .build();

        gatewayAccountEntity.setRequires3ds(false);
        gatewayAccountEntity.setSendPayerEmailToGateway(true);

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption((new CardAuthorisationGatewayRequest(chargeEntity, getValidTestCard())));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS_WITH_EMAIL),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_include_email_when_present_and_send_email_to_gateway_enabled_and_3ds_enabled() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .withEmail("test@email.invalid")
                .build();

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setSendPayerEmailToGateway(true);

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption((new CardAuthorisationGatewayRequest(chargeEntity, getValidTestCard())));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITH_EMAIL),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_not_include_email_when_present_and_send_email_to_gateway_disabled_and_3ds_disabled() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .withEmail("test@email.invalid")
                .build();

        gatewayAccountEntity.setRequires3ds(false);
        gatewayAccountEntity.setSendPayerEmailToGateway(false);

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption((new CardAuthorisationGatewayRequest(chargeEntity, getValidTestCard())));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_not_include_email_when_present_and_send_email_to_gateway_disabled_and_3ds_enabled() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .withEmail("test@email.invalid")
                .build();

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setSendPayerEmailToGateway(false);

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption((new CardAuthorisationGatewayRequest(chargeEntity, getValidTestCard())));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITHOUT_IP_ADDRESS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_not_include_email_when_not_present_and_send_email_to_gateway_enabled_and_3ds_disabled() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .withEmail(null)
                .build();

        gatewayAccountEntity.setRequires3ds(false);
        gatewayAccountEntity.setSendPayerEmailToGateway(true);

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption((new CardAuthorisationGatewayRequest(chargeEntity, getValidTestCard())));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_EXCLUDING_3DS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_not_include_email_when_not_present_and_send_email_to_gateway_enabled_and_3ds_enabled() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .withEmail(null)
                .build();

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setSendPayerEmailToGateway(true);

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption((new CardAuthorisationGatewayRequest(chargeEntity, getValidTestCard())));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_3DS_WITHOUT_IP_ADDRESS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_include_elements_for_creating_token_when_setUpPaymentInstrument_is_true() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("test-chargeId-789")
                .withAmount(500L)
                .withDescription("This is a description")
                .withTransactionId("transaction-id")
                .withEmail(null)
                .withSavePaymentInstrumentToAgreement(true)
                .withAgreementEntity(anAgreementEntity().withExternalId("test-agreement-123456").build())
                .build();

        gatewayAccountEntity.setRequires3ds(false);
        gatewayAccountEntity.setSendPayerEmailToGateway(false);
        

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption((new CardAuthorisationGatewayRequest(chargeEntity, getValidTestCard())));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());
        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_SETUP_AGREEMENT),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }
    
    @Test
    void should_send_reference_to_worldpay_instead_of_description_when_send_reference_to_gateway_is_enabled() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withReference(ServicePaymentReference.of("service-payment-reference"))
                .withTransactionId("transaction-id")
                .build();

        gatewayAccountEntity.setSendReferenceToGateway(true);

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption((new CardAuthorisationGatewayRequest(chargeEntity, getValidTestCard())));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());

        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_WITH_REFERENCE_IN_DESCRIPTION)
                .replace("{{description}}", "service-payment-reference"),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_send_description_to_worldpay_when_send_reference_to_gateway_is_disabled() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withReference(ServicePaymentReference.of("service-payment-reference"))
                .withTransactionId("transaction-id")
                .build();

        gatewayAccountEntity.setSendReferenceToGateway(false);

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption((new CardAuthorisationGatewayRequest(chargeEntity, getValidTestCard())));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());

        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_WITH_REFERENCE_IN_DESCRIPTION)
                .replace("{{description}}", "This is a description"),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }    
    
    @Test
    void should_include_browser_language_and_javascript_false_in_payload_if_flex_and_no_ddc() throws Exception {
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withAmount(500L)
                .withDescription("This is a description")
                .withReference(ServicePaymentReference.of("service-payment-reference"))
                .withTransactionId("transaction-id")
                .build();

        gatewayAccountEntity.setIntegrationVersion3ds(2);
        gatewayAccountEntity.setRequires3ds(true);

        when(authoriseClient.postRequestFor(any(URI.class), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        worldpayAuthoriseHandler.authoriseWithoutExemption((new CardAuthorisationGatewayRequest(chargeEntity, getValidTestCard())));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        verify(authoriseClient).postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), gatewayOrderArgumentCaptor.capture(), anyMap());

        assertXMLEqual(load(WORLDPAY_VALID_AUTHORISE_REQUEST_3DS_FLEX_NON_JS),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_send_successfully_an_order_for_merchant() {
        Client mockClient = mockWorldpaySuccessfulOrderSubmitResponse();

        var handlerWithRealJerseyClient = new WorldpayAuthoriseHandler(createGatewayClient(mockClient), GATEWAY_URL_MAP, new AcceptLanguageHeaderParser());

        GatewayResponse response = handlerWithRealJerseyClient.authoriseWithoutExemption(getCardAuthorisationRequest(chargeEntityFixture.build()));
        assertTrue(response.isSuccessful());
        assertTrue(response.getSessionIdentifier().isPresent());
    }

    @Test
    void should_error_if_worldpay_returns_401() {
        Client mockClient = mockWorldpayResponse(401, load(WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE));

        var handlerWithRealJerseyClient = new WorldpayAuthoriseHandler(createGatewayClient(mockClient), GATEWAY_URL_MAP, new AcceptLanguageHeaderParser());

        GatewayResponse<WorldpayOrderStatusResponse> response = 
                handlerWithRealJerseyClient.authoriseWithoutExemption(getCardAuthorisationRequest(chargeEntityFixture.build()));
        assertTrue(response.getGatewayError().isPresent());
        assertGatewayErrorEquals(response.getGatewayError().get(),
                new GatewayError("Non-success HTTP status code 401 from gateway", ErrorType.GATEWAY_ERROR));
    }

    @Test
    void should_error_if_worldpay_returns_500() {
        Client mockClient = mockWorldpayResponse(500, load(WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE));

        var handlerWithRealJerseyClient = new WorldpayAuthoriseHandler(createGatewayClient(mockClient), GATEWAY_URL_MAP, new AcceptLanguageHeaderParser());
        
        GatewayResponse<WorldpayOrderStatusResponse> response = 
                handlerWithRealJerseyClient.authoriseWithoutExemption(getCardAuthorisationRequest(chargeEntityFixture.build()));
        assertTrue(response.getGatewayError().isPresent());
        assertGatewayErrorEquals(response.getGatewayError().get(),
                new GatewayError("Non-success HTTP status code 500 from gateway", ErrorType.GATEWAY_ERROR));
    }

    private void assertGatewayErrorEquals(GatewayError actual, GatewayError expected) {
        assertNotNull(actual);
        assertThat(actual.getMessage(), is(expected.getMessage()));
        assertThat(actual.getErrorType(), is(expected.getErrorType()));
    }

    private GatewayClient createGatewayClient(Client mockClient) {
        ClientFactory mockClientFactory = mock(ClientFactory.class);
        GatewayClientFactory gatewayClientFactory = new GatewayClientFactory(mockClientFactory);
        when(mockClientFactory.createWithDropwizardClient(eq(PaymentGatewayName.WORLDPAY), any(GatewayOperation.class), any(MetricRegistry.class)))
                .thenReturn(mockClient);
        MetricRegistry mockMetricRegistry = mock(MetricRegistry.class);
        lenient().when(mockMetricRegistry.counter(anyString())).thenReturn(mock(Counter.class));
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
        GatewayAccountEntity gatewayAccount = GatewayAccountEntityFixture
                .aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName(WORLDPAY.getName())
                .withRequires3ds(false)
                .build();

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
