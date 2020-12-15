package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
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
import uk.gov.pay.connector.gateway.model.response.Gateway3DSAuthorisationResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gateway.worldpay.wallets.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.eq;
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
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_AUTHORISED_INQUIRY_RESPONSE;
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
    
    private WorldpayPaymentProvider worldpayPaymentProvider;

    private ChargeEntityFixture chargeEntityFixture;
    protected GatewayAccountEntity gatewayAccountEntity;
    private Map<String, String> gatewayAccountCredentials = Map.of("merchant_id", "MERCHANTCODE");

    @BeforeEach
    void setup() {
        worldpayPaymentProvider = new WorldpayPaymentProvider(gatewayUrlMap, authoriseClient, cancelClient, 
                inquiryClient, worldpayWalletAuthorisationHandler, worldpayAuthoriseHandler, worldpayCaptureHandler, 
                worldpayRefundHandler);
        
        gatewayAccountEntity = aServiceAccount();
        gatewayAccountEntity.setCredentials(gatewayAccountCredentials);
        chargeEntityFixture = aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity);
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
        GatewayClient.Response gatewayResponse = mock(GatewayClient.Response.class);
        when(gatewayResponse.getEntity()).thenReturn(load(WORLDPAY_AUTHORISED_INQUIRY_RESPONSE));

        ChargeEntity chargeEntity = chargeEntityFixture.build();

        when(inquiryClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenReturn(gatewayResponse);
        
        ChargeQueryResponse chargeQueryResponse = worldpayPaymentProvider.queryPaymentStatus(chargeEntity);
        
        assertThat(chargeQueryResponse.getMappedStatus(), is(Optional.of(ChargeStatus.AUTHORISATION_SUCCESS)));
        assertThat(chargeQueryResponse.foundCharge(), is(true));
    }
    
    @Test
    void should_construct_gateway_3DS_authorisation_response_with_paRequest_issuerUrl_and_machine_cookie_if_worldpay_asks_us_to_do_3ds_again() throws Exception {
        ChargeEntity chargeEntity = chargeEntityFixture.withProviderSessionId("original-machine-cookie").build();

        GatewayClient.Response authorisationSuccessResponse = mock(GatewayClient.Response.class);
        when(authorisationSuccessResponse.getEntity()).thenReturn(load(WORLDPAY_3DS_RESPONSE));
        when(authorisationSuccessResponse.getResponseCookies()).thenReturn(Map.of(WORLDPAY_MACHINE_COOKIE_NAME, "new-machine-cookie-value"));

        when(authoriseClient.postRequestFor(eq(WORLDPAY_URL), eq(chargeEntity.getGatewayAccount()), any(GatewayOrder.class),
                eq(List.of(new HttpCookie(WORLDPAY_MACHINE_COOKIE_NAME, "original-machine-cookie"))), anyMap()))
                .thenReturn(authorisationSuccessResponse);

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
