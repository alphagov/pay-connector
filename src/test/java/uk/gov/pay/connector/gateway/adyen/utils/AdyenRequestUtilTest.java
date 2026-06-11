package uk.gov.pay.connector.gateway.adyen.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.ApiKeys;
import uk.gov.pay.connector.app.adyen.BaseUrls;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.GatewayOperation;
import uk.gov.pay.connector.gateway.model.Auth3dsResult;
import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture.aCardAuthorisationGatewayRequest;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

@ExtendWith(MockitoExtension.class)
class AdyenRequestUtilTest {

    @Mock
    private AdyenGatewayConfig mockAdyenGatewayConfig;
    public final AdyenCredentials adyenCredentials = new AdyenCredentials(
            "legal_entity_id",
            "store_id",
            "account_holder_id",
            "balance_account_id");
    private CardAuthorisationGatewayRequest mockAuthoriseRequest;
    private CancelGatewayRequest mockCancelRequest;
    private CaptureGatewayRequest mockCaptureRequest;
    private Auth3dsResponseGatewayRequest mockAuth3dsResponseRequest;
    ChargeEntity chargeEntity;

    public static final String GATEWAY_TRANSACTION_ID = "gateway-transaction-id";

    @BeforeEach
    void setUp() {
        mockAuthoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails().build())
                .withCredentials(adyenCredentials)
                .withGatewayAccount(aGatewayAccountEntity().withType(TEST).build())
                .build();

        chargeEntity = new ChargeEntityFixture()
                .withGatewayAccountEntity(
                        aGatewayAccountEntity().withType(TEST).build())
                .withGatewayTransactionId(GATEWAY_TRANSACTION_ID)
                .build();
        mockCaptureRequest = CaptureGatewayRequest.valueOf(chargeEntity);
        mockCancelRequest = CancelGatewayRequest.valueOf(chargeEntity);
        var auth3dsResult = new Auth3dsResult();
        auth3dsResult.setRedirectResult("redirect-result-value");
        mockAuth3dsResponseRequest = Auth3dsResponseGatewayRequest.valueOf(chargeEntity, auth3dsResult);
    }

    @Test
    void should_create_Adyen_checkout_authorisation_URL() {
        stubCheckoutBaseUrls("https://example.com/test/v71", "https://example.com/live/v71");

        var authUrl = AdyenRequestUtil.getAuthUrl(mockAdyenGatewayConfig, mockAuthoriseRequest).toString();

        assertThat(authUrl, is("https://example.com/test/v71/payments"));
    }

    @Test
    void should_create_Adyen_checkout_payment_details_URL() {
        stubCheckoutBaseUrls("https://example.com/test/someVersion", "https://example.com/live/someVersion");

        var paymentDetailsUrl = AdyenRequestUtil.get3dsAuthUrl(mockAdyenGatewayConfig, mockAuth3dsResponseRequest).toString();

        assertThat(paymentDetailsUrl, is("https://example.com/test/v71/payments/details"));
    }

    @Test
    void should_create_Adyen_checkout_capture_URL() {
        stubCheckoutBaseUrls("https://example.com/test/v71", "https://example.com/live/v71");

        var captureUrl = AdyenRequestUtil.getCaptureUrl(mockAdyenGatewayConfig, mockCaptureRequest).toString();

        assertThat(captureUrl, is(String.format("https://example.com/test/v71/payments/%s/captures", GATEWAY_TRANSACTION_ID)));
    }


    @ParameterizedTest
    @CsvSource({
            "TEST,https://example.com/test/v71",
            "LIVE,https://example.com/live/v71"
    })
    void should_create_adyen_checkout_refund_url(GatewayAccountType gatewayAccountType, String expectedCheckoutBaseUrl) {
        stubCheckoutBaseUrls("https://example.com/test/v71", "https://example.com/live/v71");
        chargeEntity.getGatewayAccount().setType(gatewayAccountType);

        var refundEntity = new RefundEntityFixture()
                .withGatewayTransactionId(GATEWAY_TRANSACTION_ID)
                .withExternalId("refund-external-id")
                .build();
        var mockRefundRequest = RefundGatewayRequest.valueOf(
                Charge.from(chargeEntity),
                refundEntity,
                chargeEntity.getGatewayAccount(),
                chargeEntity.getGatewayAccountCredentialsEntity());

        var checkoutUrl = AdyenRequestUtil.getRefundUrl(mockAdyenGatewayConfig, mockRefundRequest).toString();
        assertThat(checkoutUrl, is(String.format("%s/payments/%s/refunds", expectedCheckoutBaseUrl, GATEWAY_TRANSACTION_ID)));
    }

    @Test
    void should_create_Adyen_checkout_cancel_URL() {
        stubCheckoutBaseUrls("https://example.com/test/v71", "https://example.com/live/v71");

        var cancelUrl = AdyenRequestUtil.getCancelUrl(mockAdyenGatewayConfig, mockCancelRequest).toString();

        assertThat(cancelUrl, is(String.format("https://example.com/test/v71/payments/%s/cancels", GATEWAY_TRANSACTION_ID)));
    }

    @Test
    void should_create_API_key_and_idempotency_key_headers_for_checkout_URL() {
        ApiKeys mockApiKeys = mock(ApiKeys.class);
        ApiKeys.CompanyAccountApiKeys mockCompanyApiKeys = mock(ApiKeys.CompanyAccountApiKeys.class);
        when(mockApiKeys.companyAccount()).thenReturn(mockCompanyApiKeys);
        when(mockCompanyApiKeys.test()).thenReturn("test");
        when(mockAdyenGatewayConfig.getApiKeys()).thenReturn(mockApiKeys);

        var headers = AdyenRequestUtil.getHeaders(mockAdyenGatewayConfig, mockAuthoriseRequest.getGatewayAccount().isLive(), GatewayOperation.AUTHORISE, "some-unique-key");

        assertThat(headers, hasEntry("X-API-Key", "test"));
        assertThat(headers, hasEntry("Idempotency-Key", "auth-some-unique-key"));
    }

    private void stubCheckoutBaseUrls(String test, String live) {
        BaseUrls mockBaseUrls = mock(BaseUrls.class);
        when(mockBaseUrls.checkout()).thenReturn(new BaseUrls.CheckoutUrls(test, live));
        when(mockAdyenGatewayConfig.getBaseUrls()).thenReturn(mockBaseUrls);
    }
}
