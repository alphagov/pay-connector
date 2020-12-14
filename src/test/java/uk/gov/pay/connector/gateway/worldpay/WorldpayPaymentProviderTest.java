package uk.gov.pay.connector.gateway.worldpay;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.net.HttpCookie;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayPaymentProvider.WORLDPAY_MACHINE_COOKIE_NAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_3DS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISED_INQUIRY_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_FLEX_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
public class WorldpayPaymentProviderTest {

    private static final URI WORLDPAY_URL = URI.create("http://worldpay.url");
    
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
    private ConnectorConfiguration configuration;
    
    @Mock
    private GatewayConfig gatewayConfig;
    
    @Mock
    protected Environment environment;
    
    @Mock
    private GatewayClientFactory gatewayClientFactory;
    
    @Mock
    private GatewayClient mockGatewayClient;

    @Mock
    private GatewayClient.Response authorisationSuccessResponse;
    
    private Map<String, String> urlMap = Map.of(TEST.toString(), WORLDPAY_URL.toString());

    private WorldpayPaymentProvider providerWithMockedGatewayClient;
    private WorldpayPaymentProvider providerWithRealGatewayClient;

    private ChargeEntityFixture chargeEntityFixture;
    protected GatewayAccountEntity gatewayAccountEntity;
    private Map<String, String> gatewayAccountCredentials = Map.of("merchant_id", "MERCHANTCODE");

    @BeforeEach
    void setup() {
        lenient().when(mockMetricRegistry.histogram(anyString())).thenReturn(mockHistogram);
        lenient().when(mockMetricRegistry.counter(anyString())).thenReturn(mockCounter);
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
        gatewayAccountEntity.setCredentials(gatewayAccountCredentials);
        chargeEntityFixture = aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity);
    }

    @Test
    void should_get_payment_provider_name() {
        assertThat(providerWithMockedGatewayClient.getPaymentGatewayName().getName(), is("worldpay"));
    }

    @Test
    void should_generate_transactionId() {
        assertThat(providerWithMockedGatewayClient.generateTransactionId().isPresent(), is(true));
        assertThat(providerWithMockedGatewayClient.generateTransactionId().get(), is(instanceOf(String.class)));
    }

    @Test
    void test_refund_request_contains_reference() throws Exception {
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
    void should_include_paResponse_In_3ds_second_order() throws Exception {
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

        assertXMLEqual(load(WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST),
                gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    @Test
    void should_include_completed_authentication_in_second_order_when_paRequest_not_in_frontend_request() throws Exception {
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
    void assert_authorization_header_is_passed_to_gateway_client() throws Exception {
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
        assertThat(headers.getValue(), is(AuthUtil.getGatewayAccountCredentialsAsAuthHeader(gatewayAccountCredentials)));
    }

    @Test
    void should_successfully_query_payment_status() throws Exception {
        GatewayClient.Response gatewayResponse = mock(GatewayClient.Response.class);
        when(gatewayResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISED_INQUIRY_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture.build();

        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(gatewayResponse);

        ChargeQueryResponse chargeQueryResponse = providerWithMockedGatewayClient.queryPaymentStatus(chargeEntity);
        
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.of(ChargeStatus.AUTHORISATION_SUCCESS)));
        assertThat(chargeQueryResponse.foundCharge(), is(true));
    }
    
    @Test
    void should_construct_gateway_3DS_authorisation_response_with_paRequest_issuerUrl_and_machine_cookie_if_worldpay_asks_us_to_do_3ds_again() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture.withProviderSessionId("original-machine-cookie").build();

        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_3DS_RESPONSE));
        when(authorisationSuccessResponse.getResponseCookies()).thenReturn(Map.of(WORLDPAY_MACHINE_COOKIE_NAME, "new-machine-cookie-value"));

        when(mockGatewayClient.postRequestFor(eq(WORLDPAY_URL), eq(chargeEntity.getGatewayAccount()), any(GatewayOrder.class),
                eq(List.of(new HttpCookie(WORLDPAY_MACHINE_COOKIE_NAME, "original-machine-cookie"))), anyMap()))
                .thenReturn(authorisationSuccessResponse);

        var auth3dsResult = new Auth3dsResult();
        auth3dsResult.setPaResponse("pa-response");
        var auth3dsResponseGatewayRequest = new Auth3dsResponseGatewayRequest(chargeEntity, auth3dsResult);

        Gateway3DSAuthorisationResponse result = providerWithMockedGatewayClient.authorise3dsResponse(auth3dsResponseGatewayRequest);

        assertThat(result.getGateway3dsRequiredParams().isPresent(), is(true));
        assertThat(result.getGateway3dsRequiredParams().get().toAuth3dsRequiredEntity().getPaRequest(), 
                is("eJxVUsFuwjAM/ZWK80aSUgpFJogNpHEo2hjTzl"));
        assertThat(result.getGateway3dsRequiredParams().get().toAuth3dsRequiredEntity().getIssuerUrl(),
                is("https://secure-test.worldpay.com/jsp/test/shopper/ThreeDResponseSimulator.jsp"));

        assertThat(result.getProviderSessionIdentifier().isPresent(), is (true));
        assertThat(result.getProviderSessionIdentifier().get(), is (ProviderSessionIdentifier.of("new-machine-cookie-value")));
    }

    @Test
    void should_error_if_worldpay_returns_401() {
        mockWorldpayErrorResponse(401);
        GatewayResponse<WorldpayOrderStatusResponse> response = providerWithRealGatewayClient.authorise(getCardAuthorisationRequest());
        assertTrue(response.getGatewayError().isPresent());
        assertGatewayErrorEquals(response.getGatewayError().get(), 
                new GatewayError("Non-success HTTP status code 401 from gateway", ErrorType.GATEWAY_ERROR));
    }

    @Test
    void should_error_if_worldpay_returns_500() {
        mockWorldpayErrorResponse(500);
        GatewayResponse<WorldpayOrderStatusResponse> response = providerWithRealGatewayClient.authorise(getCardAuthorisationRequest());
        assertTrue(response.getGatewayError().isPresent());
        assertGatewayErrorEquals(response.getGatewayError().get(), 
                new GatewayError("Non-success HTTP status code 500 from gateway", ErrorType.GATEWAY_ERROR));
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
        gatewayAccount.setCredentials(Map.of(
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
        String errorResponse = load(WORLDPAY_AUTHORISATION_PARES_PARSE_ERROR_RESPONSE);
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
