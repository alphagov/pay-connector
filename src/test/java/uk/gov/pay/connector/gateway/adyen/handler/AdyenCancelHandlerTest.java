package uk.gov.pay.connector.gateway.adyen.handler;

import com.jayway.jsonassert.JsonAssert;
import io.dropwizard.jackson.Jackson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.app.adyen.ApiKeys.CompanyAccountApiKeys;
import uk.gov.pay.connector.app.adyen.BaseUrls.CheckoutUrls;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayException.GenericGatewayException;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.then;
import static uk.gov.pay.connector.app.adyen.ApiKeysFixture.someApiKeys;
import static uk.gov.pay.connector.app.adyen.BaseUrlsFixture.someBaseUrls;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.model.response.BaseCancelResponse.CancelStatus.SUBMITTED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.LIVE;

@ExtendWith(MockitoExtension.class)
class AdyenCancelHandlerTest {

    private static final String A_MERCHANT_ACCOUNT_ID = "a-merchant-account-id";
    private static final String AN_EXTERNAL_ID = "a-charge-external-id";
    private static final String LIVE_CHECKOUT_BASE_URL = "https://checkout.example.com";
    private static final String A_GATEWAY_TRANSACTION_ID = "a-gateway-transaction-id";
    private static final String TEST_COMPANY_ACCOUNT_API_KEY = "test-company-account-API-key"; // pragma: allowlist secret
    private static final String LIVE_COMPANY_ACCOUNT_API_KEY = "live-company-account-API-key"; // pragma: allowlist secret


    @Mock
    private GatewayClient mockGatewayClient;
    @Captor
    private ArgumentCaptor<GatewayClientPostRequest> captor;

    private AdyenCancelHandler cancelHandler;

    @BeforeEach
    void setUp() {
        var mockAdyenIds = new AdyenIds(A_MERCHANT_ACCOUNT_ID, A_MERCHANT_ACCOUNT_ID);
        var mockAdyenGatewayConfig = mock(AdyenGatewayConfig.class);
        given(mockAdyenGatewayConfig.getMerchantAccountIds())
                .willReturn(mockAdyenIds);
        var apiKeys = someApiKeys()
                .withCompanyAccount(
                        new CompanyAccountApiKeys(TEST_COMPANY_ACCOUNT_API_KEY, LIVE_COMPANY_ACCOUNT_API_KEY))
                .build();
        given(mockAdyenGatewayConfig.getApiKeys())
                .willReturn(apiKeys);
        var baseUrls = someBaseUrls()
                .withCheckout(new CheckoutUrls("https://example.com", LIVE_CHECKOUT_BASE_URL))
                .build();
        given(mockAdyenGatewayConfig.getBaseUrls())
                .willReturn(baseUrls);
        var mockConfiguration = mock(ConnectorConfiguration.class);
        given(mockConfiguration.getAdyenGatewayConfig())
                .willReturn(mockAdyenGatewayConfig);
        cancelHandler = new AdyenCancelHandler(
                mockGatewayClient,
                mockAdyenGatewayConfig,
                new AdyenRequestFactory(mockConfiguration),
                new JsonObjectMapper(Jackson.newObjectMapper()));
    }

    @Test
    void should_pass_the_gateway_transaction_ID_as_the_path_parameter_to_the_cancel_request() throws Exception {
        var request = CancelGatewayRequest.valueOf(
                aValidChargeEntity()
                        .withGatewayTransactionId(A_GATEWAY_TRANSACTION_ID)
                        .withGatewayAccountEntity(makeGatewayAccountEntityForAccountType(LIVE))
                        .build());

        cancelHandler.cancel(request);

        then(mockGatewayClient).should()
                .postRequestFor(captor.capture());
        assertThat(captor.getValue().getUrl().toString(), is(
                LIVE_CHECKOUT_BASE_URL + "/payments/" + A_GATEWAY_TRANSACTION_ID + "/cancels"));
    }

    @Test
    void should_POST_a_GatewayOrder_with_reference_and_merchant_account_in_payload() throws Exception {
        var request = CancelGatewayRequest.valueOf(
                aValidChargeEntity()
                        .withExternalId(AN_EXTERNAL_ID)
                        .withGatewayAccountEntity(makeGatewayAccountEntityForAccountType(LIVE))
                        .build());

        cancelHandler.cancel(request);

        then(mockGatewayClient).should()
                .postRequestFor(captor.capture());
        var gatewayOrderPayload = captor.getValue()
                .getGatewayOrder()
                .getPayload();
        JsonAssert.with(gatewayOrderPayload)
                .assertEquals("reference", AN_EXTERNAL_ID)
                .assertEquals("merchantAccount", A_MERCHANT_ACCOUNT_ID);
    }

    @ParameterizedTest
    @CsvSource({
            "LIVE," + LIVE_COMPANY_ACCOUNT_API_KEY,
            "TEST," + TEST_COMPANY_ACCOUNT_API_KEY
    })
    void should_POST_with_company_API_key_as_X_API_Key_header(GatewayAccountType gatewayAccountType, String expectedApiKey) throws Exception {
        var request = CancelGatewayRequest.valueOf(
                aValidChargeEntity()
                        .withGatewayAccountEntity(makeGatewayAccountEntityForAccountType(gatewayAccountType))
                        .build());

        cancelHandler.cancel(request);

        then(mockGatewayClient).should()
                .postRequestFor(captor.capture());
        var headers = captor.getValue().getHeaders();
        assertThat(headers, hasEntry("X-API-Key", expectedApiKey));
    }

    @ParameterizedTest
    @EnumSource(GatewayAccountType.class)
    void should_POST_request_with_account_type(GatewayAccountType type) throws Exception {
        var request = CancelGatewayRequest.valueOf(
                aValidChargeEntity()
                        .withGatewayAccountEntity(makeGatewayAccountEntityForAccountType(type))
                        .build());

        cancelHandler.cancel(request);

        then(mockGatewayClient).should()
                .postRequestFor(captor.capture());
        var accountType = captor.getValue().getGatewayAccountType();
        assertThat(accountType, is(type.toString()));
    }

    @Test
    void should_return_SUBMITTED_cancel_status() {
        var request = CancelGatewayRequest.valueOf(
                aValidChargeEntity()
                        .withGatewayAccountEntity(makeGatewayAccountEntityForAccountType(LIVE))
                        .build());

        var gatewayResponse = cancelHandler.cancel(request);

        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get().cancelStatus(), is(SUBMITTED));
    }

    @ParameterizedTest
    @ValueSource(classes = {
            GenericGatewayException.class, GatewayConnectionTimeoutException.class, GatewayErrorException.class})
    void should_catch_client_exception_and_return_an_error_in_the_response(Class<? extends GatewayException> exceptionClass) throws GatewayException {
        var request = CancelGatewayRequest.valueOf(
                aValidChargeEntity()
                        .withGatewayAccountEntity(makeGatewayAccountEntityForAccountType(LIVE))
                        .build());
        given(mockGatewayClient.postRequestFor(any()))
                .willThrow(exceptionClass);

        var gatewayResponse = cancelHandler.cancel(request);

        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
    }

    private static GatewayAccountEntity makeGatewayAccountEntityForAccountType(GatewayAccountType type) {
        return aGatewayAccountEntity()
                .withType(type)
                .build();
    }
}
