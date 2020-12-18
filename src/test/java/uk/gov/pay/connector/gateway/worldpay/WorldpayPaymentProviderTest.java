package uk.gov.pay.connector.gateway.worldpay;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.gateway.ChargeQueryResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStringifier;
import uk.gov.pay.connector.gateway.util.AuthorisationRequestSummaryStructuredLogging;
import uk.gov.pay.connector.gateway.worldpay.wallets.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentprocessor.service.AuthorisationService;
import uk.gov.pay.connector.paymentprocessor.service.CardExecutorService;

import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.gateway.util.XMLUnmarshaller.unmarshall;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccount.model.Worldpay3dsFlexCredentialsEntity.Worldpay3dsFlexCredentialsEntityBuilder.aWorldpay3dsFlexCredentialsEntity;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISED_INQUIRY_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_EXEMPTION_REQUEST_SOFT_DECLINE_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_FLEX_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
public class WorldpayPaymentProviderTest {

    private static final URI WORLDPAY_URL = URI.create("http://worldpay.url");

    private Map<String, URI> gatewayUrlMap = Map.of(TEST.toString(), WORLDPAY_URL);
    
    @Mock private GatewayClient authoriseClient;
    @Mock private GatewayClient cancelClient;
    @Mock private GatewayClient inquiryClient;
    @Mock private WorldpayWalletAuthorisationHandler worldpayWalletAuthorisationHandler;
    @Mock private WorldpayAuthoriseHandler worldpayAuthoriseHandler;
    @Mock private WorldpayCaptureHandler worldpayCaptureHandler;
    @Mock private WorldpayRefundHandler worldpayRefundHandler;
    @Mock private GatewayClient.Response response = mock(GatewayClient.Response.class);
    @Mock private Appender<ILoggingEvent> mockAppender;
    
    private WorldpayPaymentProvider worldpayPaymentProvider;

    private ChargeEntityFixture chargeEntityFixture;
    protected GatewayAccountEntity gatewayAccountEntity;
    private Map<String, String> gatewayAccountCredentials = Map.of("merchant_id", "MERCHANTCODE");

    @BeforeEach
    void setup() {
        worldpayPaymentProvider = new WorldpayPaymentProvider(gatewayUrlMap, authoriseClient, cancelClient, 
                inquiryClient, worldpayWalletAuthorisationHandler, worldpayAuthoriseHandler, worldpayCaptureHandler, 
                worldpayRefundHandler, new AuthorisationRequestSummaryStringifier(), 
                new AuthorisationService(mock(CardExecutorService.class), mock(Environment.class)), 
                new AuthorisationRequestSummaryStructuredLogging());
        
        gatewayAccountEntity = aServiceAccount();
        gatewayAccountEntity.setCredentials(gatewayAccountCredentials);
        chargeEntityFixture = aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity);

        Logger root = (Logger) LoggerFactory.getLogger(WorldpayPaymentProvider.class);
        root.setLevel(Level.INFO);
        root.addAppender(mockAppender);
    }

    @Test
    void should_retry_without_exemption_flag_when_authorising_with_exemption_flag_results_in_soft_decline() throws Exception {

        gatewayAccountEntity.setRequires3ds(true);
        gatewayAccountEntity.setIntegrationVersion3ds(1);
        gatewayAccountEntity.setWorldpay3dsFlexCredentialsEntity(aWorldpay3dsFlexCredentialsEntity().withExemptionEngine(true).build());
        chargeEntityFixture.withGatewayAccountEntity(gatewayAccountEntity);
        chargeEntityFixture.withStatus(ChargeStatus.AUTHORISATION_READY);
        ChargeEntity chargeEntity = chargeEntityFixture.build();
        
        var cardAuthRequest = new CardAuthorisationGatewayRequest(chargeEntity, anAuthCardDetails().build());

        var firstResponse = getGatewayResponse(WORLDPAY_EXEMPTION_REQUEST_SOFT_DECLINE_RESPONSE);
        var secondResponse = getGatewayResponse(WORLDPAY_AUTHORISATION_SUCCESS_RESPONSE);
        
        when(worldpayAuthoriseHandler.authorise(cardAuthRequest)).thenReturn(firstResponse);
        when(worldpayAuthoriseHandler.authorise(cardAuthRequest, true)).thenReturn(secondResponse);

        GatewayResponse<WorldpayOrderStatusResponse> response = worldpayPaymentProvider.authorise(cardAuthRequest);
        
        assertTrue(response.getBaseResponse().isPresent());
        assertEquals(secondResponse.getBaseResponse().get(), response.getBaseResponse().get());
        
        verify(worldpayAuthoriseHandler).authorise(cardAuthRequest);
        verify(worldpayAuthoriseHandler).authorise(cardAuthRequest, true);
        
        ArgumentCaptor<LoggingEvent> loggingEventArgumentCaptor = ArgumentCaptor.forClass(LoggingEvent.class);
        verify(mockAppender, times(1)).doAppend(loggingEventArgumentCaptor.capture());
        String log = loggingEventArgumentCaptor.getAllValues().get(0).getMessage();
        assertTrue(log.contains(format("Authorisation with billing address and with 3DS data and without device data " +
                "collection result and with exemption for %s", chargeEntity.getExternalId())));
        assertTrue(log.contains("Worldpay authorisation response (orderCode: transaction-id, lastEvent: REFUSED, " +
                "result: REJECTED, reason: HIGH_RISK)"));
        assertTrue(log.contains("AUTHORISATION READY -> AUTHORISATION READY"));
    }

    private GatewayResponse<WorldpayOrderStatusResponse> getGatewayResponse(String responseFile) throws Exception {
        GatewayResponse.GatewayResponseBuilder<WorldpayOrderStatusResponse> responseBuilder = responseBuilder();
        responseBuilder.withResponse(unmarshall(load(responseFile), WorldpayOrderStatusResponse.class));
        return responseBuilder.build();
    }

    @Test
    void should_get_payment_provider_name() {
        assertThat(worldpayPaymentProvider.getPaymentGatewayName().getName(), is("worldpay"));
    }

    @Test
    void should_generate_transactionId() {
        assertThat(worldpayPaymentProvider.generateTransactionId().isPresent(), is(true));
        assertThat(worldpayPaymentProvider.generateTransactionId().get(), is(instanceOf(String.class)));
    }
    
    @Test
    void should_include_paResponse_In_3ds_second_order() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 401 from gateway"));
        
        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(chargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(authoriseClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyList(),
                anyMap());

        assertXMLEqual(load(WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_include_completed_authentication_in_second_order_when_paRequest_not_in_frontend_request() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 401 from gateway"));
        
        var request = new Auth3dsResponseGatewayRequest(chargeEntity, new Auth3dsResult());
        worldpayPaymentProvider.authorise3dsResponse(request);

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(authoriseClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyList(),
                anyMap());

        assertXMLEqual(load(WORLDPAY_VALID_3DS_FLEX_RESPONSE_AUTH_WORLDPAY_REQUEST),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_include_provider_session_id_when_available_for_charge() throws Exception {
        String providerSessionId = "provider-session-id";
        ChargeEntity chargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("MyUniqueTransactionId!")
                .withProviderSessionId(providerSessionId)
                .build();

        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));
        
        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(chargeEntity));

        ArgumentCaptor<List<HttpCookie>> cookies = ArgumentCaptor.forClass(List.class);

        verify(authoriseClient).postRequestFor(
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
    void assert_authorization_header_is_passed_to_gateway_client() throws Exception {
        String providerSessionId = "provider-session-id";
        ChargeEntity mockChargeEntity = chargeEntityFixture
                .withExternalId("uniqueSessionId")
                .withTransactionId("MyUniqueTransactionId!")
                .withProviderSessionId(providerSessionId)
                .build();

        when(authoriseClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyList(), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Unexpected HTTP status code 400 from gateway"));
        
        worldpayPaymentProvider.authorise3dsResponse(get3dsResponseGatewayRequest(mockChargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);

        verify(authoriseClient).postRequestFor(
                eq(WORLDPAY_URL),
                eq(gatewayAccountEntity),
                gatewayOrderArgumentCaptor.capture(),
                anyList(),
                headers.capture());

        assertThat(headers.getValue().size(), is(1));
        assertThat(headers.getValue(), is(AuthUtil.getGatewayAccountCredentialsAsAuthHeader(gatewayAccountCredentials)));
    }

    @Test
    void should_successfully_query_payment_status() throws Exception {
        when(response.getEntity()).thenReturn(load(WORLDPAY_AUTHORISED_INQUIRY_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture.build();

        when(inquiryClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(response);
        
        ChargeQueryResponse chargeQueryResponse = worldpayPaymentProvider.queryPaymentStatus(chargeEntity);
        
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.of(ChargeStatus.AUTHORISATION_SUCCESS)));
        assertThat(chargeQueryResponse.foundCharge(), is(true));
    }
    
    @Test
    void should_construct_gateway_3DS_authorisation_response_with_paRequest_issuerUrl_and_machine_cookie_if_worldpay_asks_us_to_do_3ds_again() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture.withProviderSessionId("original-machine-cookie").build();

        when(response.getEntity()).thenReturn(load(WORLDPAY_3DS_RESPONSE));
        when(response.getResponseCookies()).thenReturn(Map.of(WORLDPAY_MACHINE_COOKIE_NAME, "new-machine-cookie-value"));

        when(authoriseClient.postRequestFor(eq(WORLDPAY_URL), eq(chargeEntity.getGatewayAccount()), any(GatewayOrder.class),
                eq(List.of(new HttpCookie(WORLDPAY_MACHINE_COOKIE_NAME, "original-machine-cookie"))), anyMap()))
                .thenReturn(response);

        var auth3dsResult = new Auth3dsResult();
        auth3dsResult.setPaResponse("pa-response");
        var auth3dsResponseGatewayRequest = new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsResult);

        Gateway3DSAuthorisationResponse result = worldpayPaymentProvider.authorise3dsResponse(auth3dsResponseGatewayRequest);

        assertThat(result.getGateway3dsRequiredParams().isPresent(), is(true));
        assertThat(result.getGateway3dsRequiredParams().get().toAuth3dsRequiredEntity().getPaRequest(), 
                is("eJxVUsFuwjAM/ZWK80aSUgpFJogNpHEo2hjTzl"));
        assertThat(result.getGateway3dsRequiredParams().get().toAuth3dsRequiredEntity().getIssuerUrl(),
                is("https://secure-test.worldpay.com/jsp/test/shopper/ThreeDResponseSimulator.jsp"));

        assertThat(result.getProviderSessionIdentifier().isPresent(), is (true));
        assertThat(result.getProviderSessionIdentifier().get(), is (ProviderSessionIdentifier.of("new-machine-cookie-value")));
    }

    private Auth3dsResponseGatewayRequest get3dsResponseGatewayRequest(ChargeEntity chargeEntity) {
        Auth3dsResult auth3dsResult = new Auth3dsResult();
        auth3dsResult.setPaResponse("I am an opaque 3D Secure PA response from the card issuer");
        return new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsResult);
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
}
