package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayEncryptedPaymentData;
import uk.gov.pay.connector.wallets.googlepay.api.WorldpayGooglePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.LocalDate;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseApplePayOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseGooglePayOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseRecurringOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayDeleteTokenOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayRefundOrderRequestBuilder;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayDecryptedPaymentDataFixture.anApplePayDecryptedPaymentData;
import static uk.gov.pay.connector.model.domain.applepay.WalletPaymentInfoFixture.aWalletPaymentInfo;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_SPECIAL_CHAR_VALID_CAPTURE_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_RECURRING_WORLDPAY_REQUEST_WITHOUT_SCHEME_IDENTIFIER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_RECURRING_WORLDPAY_REQUEST_WITH_SCHEME_IDENTIFIER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST_MIN_DATA;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITHOUT_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITH_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_CANCEL_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_CAPTURE_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_DELETE_TOKEN_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_REFUND_WORLDPAY_REQUEST;

class WorldpayOrderRequestBuilderTest {

    protected static final String GOOGLE_PAY_ACCEPT_HEADER = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,/;q=0.8";
    protected static final String GOOGLE_PAY_USER_AGENT_HEADER = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.183 Safari/537.36";

    private static final WalletPaymentInfo googlePayWalletPaymentInfoFor3ds = aWalletPaymentInfo()
            .withLastDigitsCardNumber("4242")
            .withBrand("visa")
            .withCardType(PayersCardType.DEBIT)
            .withCardholderName("Example Name")
            .withEmail("example@test.example")
            .withAcceptHeader(GOOGLE_PAY_ACCEPT_HEADER)
            .withUserAgentHeader(GOOGLE_PAY_USER_AGENT_HEADER)
            .withIpAddress("8.8.8.8")
            .build();

    private static final AppleDecryptedPaymentData validApplePayData =
            anApplePayDecryptedPaymentData()
                    .withApplePaymentInfo(
                            aWalletPaymentInfo()
                                    .withLastDigitsCardNumber("4242").build())
                    .build();
    public static final GooglePayEncryptedPaymentData GOOGLE_PAY_ENCRYPTED_PAYMENT_DATA = new GooglePayEncryptedPaymentData(
            "aSignedMessage",
            "ECv1",
            "MEYCIQC+a+AzSpQGr42UR1uTNX91DQM2r7SeKwzNs0UPoeSrrQIhAPpSzHjYTvvJGGzWwli8NRyHYE/diQMLL8aXqm9VIrwl"
    );

    @Test
    void shouldGenerateValidAuthoriseRecurringOrderRequestWithSchemeIdentifier() throws Exception {
        GatewayOrder actualRequest = aWorldpayAuthoriseRecurringOrderRequestBuilder()
                .withPaymentTokenId("test-payment-token-123456")
                .withSchemeTransactionIdentifier("test-transaction-id-999999")
                .withAgreementId("test-agreement-123456")
                .withTransactionId("test-transaction-id-123")
                .withMerchantCode("MIT-MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_RECURRING_WORLDPAY_REQUEST_WITH_SCHEME_IDENTIFIER), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseRecurringOrderRequestWithOutSchemeIdentifier() throws Exception {
        GatewayOrder actualRequest = aWorldpayAuthoriseRecurringOrderRequestBuilder()
                .withPaymentTokenId("test-payment-token-123456")
                .withAgreementId("test-agreement-123456")
                .withTransactionId("test-transaction-id-123")
                .withMerchantCode("MIT-MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_RECURRING_WORLDPAY_REQUEST_WITHOUT_SCHEME_IDENTIFIER), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuth3dsResponseOrderRequest() throws Exception {
        GatewayOrder actualRequest = aWorldpay3dsResponseAuthOrderRequestBuilder()
                .withPaResponse3ds("I am an opaque 3D Secure PA response from the card issuer")
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_3DS, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseApplePayOrderRequest() throws Exception {
        GatewayOrder actualRequest = aWorldpayAuthoriseApplePayOrderRequestBuilder()
                .withAppleDecryptedPaymentData(validApplePayData)
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_APPLE_PAY, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseApplePayOrderRequest_withMinData() throws Exception {
        AppleDecryptedPaymentData validData =
                anApplePayDecryptedPaymentData()
                        .withEciIndicator(null)
                        .withApplePaymentInfo(
                                aWalletPaymentInfo()
                                        .withCardholderName(null)
                                        .withLastDigitsCardNumber("4242").build())
                        .build();
        GatewayOrder actualRequest = aWorldpayAuthoriseApplePayOrderRequestBuilder()
                .withAppleDecryptedPaymentData(validData)
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST_MIN_DATA), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_APPLE_PAY, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseGooglePayOrderRequest() throws Exception {
        WalletPaymentInfo walletPaymentInfo = aWalletPaymentInfo().build();
        WorldpayGooglePayAuthRequest validGooglePayData = new WorldpayGooglePayAuthRequest(walletPaymentInfo, GOOGLE_PAY_ENCRYPTED_PAYMENT_DATA);

        GatewayOrder actualRequest = aWorldpayAuthoriseGooglePayOrderRequestBuilder()
                .withGooglePayPaymentData(validGooglePayData)
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_GOOGLE_PAY, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseGooglePay3dsOrderRequestWithoutIpAddress() throws Exception {
        WorldpayGooglePayAuthRequest validGooglePay3dsData = new WorldpayGooglePayAuthRequest(googlePayWalletPaymentInfoFor3ds, GOOGLE_PAY_ENCRYPTED_PAYMENT_DATA);

        GatewayOrder actualRequest = aWorldpayAuthoriseGooglePayOrderRequestBuilder()
                .withGooglePayPaymentData(validGooglePay3dsData)
                .with3dsRequired(true)
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withAcceptHeader(GOOGLE_PAY_ACCEPT_HEADER)
                .withUserAgentHeader(GOOGLE_PAY_USER_AGENT_HEADER)
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITHOUT_IP_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_GOOGLE_PAY, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseGooglePay3dsOrderRequestWithIpAddress() throws Exception {
        WorldpayGooglePayAuthRequest validGooglePay3dsData = new WorldpayGooglePayAuthRequest(googlePayWalletPaymentInfoFor3ds, GOOGLE_PAY_ENCRYPTED_PAYMENT_DATA);

        GatewayOrder actualRequest = aWorldpayAuthoriseGooglePayOrderRequestBuilder()
                .withGooglePayPaymentData(validGooglePay3dsData)
                .with3dsRequired(true)
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withAcceptHeader(GOOGLE_PAY_ACCEPT_HEADER)
                .withUserAgentHeader(GOOGLE_PAY_USER_AGENT_HEADER)
                .withPayerIpAddress("8.8.8.8")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITH_IP_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_GOOGLE_PAY, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseGooglePay3dsOrderRequestWhen3dsDisabled() throws Exception {
        WorldpayGooglePayAuthRequest validGooglePay3dsData = new WorldpayGooglePayAuthRequest(googlePayWalletPaymentInfoFor3ds, GOOGLE_PAY_ENCRYPTED_PAYMENT_DATA);

        GatewayOrder actualRequest = aWorldpayAuthoriseGooglePayOrderRequestBuilder()
                .withGooglePayPaymentData(validGooglePay3dsData)
                .with3dsRequired(false)
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_GOOGLE_PAY, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidCaptureOrderRequest() throws Exception {
        var date = LocalDate.of(2013, 2, 23);

        GatewayOrder actualRequest = aWorldpayCaptureOrderRequestBuilder()
                .withDate(date)
                .withMerchantCode("MERCHANTCODE")
                .withAmount("500")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_CAPTURE_WORLDPAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.CAPTURE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidCaptureOrderRequestWithSpecialCharactersInStrings() throws Exception {
        var date = LocalDate.of(2013, 2, 23);

        GatewayOrder actualRequest = aWorldpayCaptureOrderRequestBuilder()
                .withDate(date)
                .withMerchantCode("MERCHANTCODE")
                .withAmount("500")
                .withTransactionId("MyUniqueTransactionId <!-- & > ")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_SPECIAL_CHAR_VALID_CAPTURE_WORLDPAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.CAPTURE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidCancelOrderRequest() throws Exception {

        GatewayOrder actualRequest = aWorldpayCancelOrderRequestBuilder()
                .withMerchantCode("MERCHANTCODE")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        String expectedRequestBody = TestTemplateResourceLoader.load(WORLDPAY_VALID_CANCEL_WORLDPAY_REQUEST)
                .replace("{{merchantCode}}", "MERCHANTCODE")
                .replace("{{transactionId}}", "MyUniqueTransactionId!");

        assertXMLEqual(expectedRequestBody, actualRequest.getPayload());
        assertEquals(OrderRequestType.CANCEL, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidRefundOrderRequest() throws Exception {

        GatewayOrder actualRequest = aWorldpayRefundOrderRequestBuilder()
                .withReference("reference")
                .withAmount("200")
                .withMerchantCode("MERCHANTCODE")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        String expectedRequestBody = TestTemplateResourceLoader.load(WORLDPAY_VALID_REFUND_WORLDPAY_REQUEST)
                .replace("{{merchantCode}}", "MERCHANTCODE")
                .replace("{{transactionId}}", "MyUniqueTransactionId!")
                .replace("{{refundReference}}", "reference")
                .replace("{{amount}}", "200");

        assertXMLEqual(expectedRequestBody, actualRequest.getPayload());
        assertEquals(OrderRequestType.REFUND, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidDeleteTokenRequest() throws Exception {
        GatewayOrder actualRequest = aWorldpayDeleteTokenOrderRequestBuilder()
                .withAgreementId("test-agreement-123")
                .withPaymentTokenId("test-paymentToken-789")
                .withMerchantCode("MYMERCHANT")
                .build();

        String expectedRequestBody = TestTemplateResourceLoader.load(WORLDPAY_VALID_DELETE_TOKEN_REQUEST)
                .replace("{{merchantCode}}", "MYMERCHANT")
                .replace("{{agreementId}}", "test-agreement-123")
                .replace("{{paymentTokenId}}", "test-paymentToken-789");

        assertXMLEqual(expectedRequestBody, actualRequest.getPayload());
        assertEquals(OrderRequestType.DELETE_STORED_PAYMENT_DETAILS, actualRequest.getOrderRequestType());
    }

    private AuthCardDetails getValidTestCard(Address address) {
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Mr. Payment")
                .withCardNo("4111111111111111")
                .withCvc("123")
                .withEndDate(CardExpiryDate.valueOf("12/15"))
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }
}
