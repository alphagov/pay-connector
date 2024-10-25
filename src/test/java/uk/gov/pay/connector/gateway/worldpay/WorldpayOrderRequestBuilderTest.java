package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayEncryptedPaymentData;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayPaymentInfo;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpay3dsResponseAuthOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseApplePayOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseGooglePayOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayAuthoriseRecurringOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayDeleteTokenOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayRefundOrderRequestBuilder;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayDecryptedPaymentDataFixture.anApplePayDecryptedPaymentData;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayPaymentInfoFixture.anApplePayPaymentInfo;
import static uk.gov.pay.connector.model.domain.googlepay.GooglePayPaymentInfoFixture.aGooglePayPaymentInfo;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_SPECIAL_CHAR_VALID_AUTHORISE_WORLDPAY_REQUEST_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_SPECIAL_CHAR_VALID_CAPTURE_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_3DS_RESPONSE_AUTH_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_RECURRING_WORLDPAY_REQUEST_WITHOUT_SCHEME_IDENTIFIER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_RECURRING_WORLDPAY_REQUEST_WITH_SCHEME_IDENTIFIER;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_INCLUDING_STATE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_MIN_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST_MIN_DATA;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITHOUT_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_3DS_REQUEST_WITH_IP_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_FULL_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_STATE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_MIN_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_WITHOUT_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_CANCEL_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_CAPTURE_WORLDPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_DELETE_TOKEN_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_REFUND_WORLDPAY_REQUEST;
import static wiremock.org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;

class WorldpayOrderRequestBuilderTest {

    protected static final String GOOGLE_PAY_ACCEPT_HEADER = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,/;q=0.8";
    protected static final String GOOGLE_PAY_USER_AGENT_HEADER = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.183 Safari/537.36";

    private static final GooglePayPaymentInfo googlePayWalletPaymentInfoFor3ds = aGooglePayPaymentInfo()
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
                            anApplePayPaymentInfo()
                                    .withLastDigitsCardNumber("4242").build())
                    .build();
    public static final GooglePayEncryptedPaymentData GOOGLE_PAY_ENCRYPTED_PAYMENT_DATA = new GooglePayEncryptedPaymentData(
            "aSignedMessage",
            "ECv1",
            "MEYCIQC+a+AzSpQGr42UR1uTNX91DQM2r7SeKwzNs0UPoeSrrQIhAPpSzHjYTvvJGGzWwli8NRyHYE/diQMLL8aXqm9VIrwl"
    );

    @Test
    void shouldGenerateValidAuthoriseOrderRequestForAddressWithMinimumFields() throws Exception {

        Address minAddress = new Address("123 My Street", null, "SW8URR", "London", null, "GB");

        AuthCardDetails authCardDetails = getValidTestCard(minAddress);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_MIN_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

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
    void shouldGenerateValidAuthoriseOrderRequestForAddressWithMinimumFieldsWhen3dsEnabled() throws Exception {

        Address minAddress = new Address("123 My Street", null, "SW8URR", "London", null, "GB");

        AuthCardDetails authCardDetails = getValidTestCard(minAddress);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .with3dsRequired(true)
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_MIN_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseOrderRequestForAddressWithState() throws Exception {

        Address usAddress = new Address("10 WCB", null, "20500", "Washington D.C.", null, "US");

        AuthCardDetails authCardDetails = getValidTestCard(usAddress);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_INCLUDING_STATE), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseOrderRequestForAddressWithStateWhen3dsEnabled() throws Exception {

        Address usAddress = new Address("10 WCB", null, "20500", "Washington D.C.", null, "US");

        AuthCardDetails authCardDetails = getValidTestCard(usAddress);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .with3dsRequired(true)
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_3DS_REQUEST_INCLUDING_STATE), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseOrderRequestForAddressWithAllFields() throws Exception {

        Address fullAddress = new Address("123 My Street", "This road", "SW8URR", "London", "London county", "GB");

        AuthCardDetails authCardDetails = getValidTestCard(fullAddress);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_FULL_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseOrderRequestWhenSpecialCharactersInUserInput() throws Exception {

        Address address = new Address("123 & My Street", "This road -->", "SW8 > URR", "London !>", null, "GB");

        AuthCardDetails authCardDetails = getValidTestCard(address);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description with <!-- ")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_SPECIAL_CHAR_VALID_AUTHORISE_WORLDPAY_REQUEST_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    void shouldGenerateValidAuthoriseOrderRequestWhenAddressIsMissing() throws Exception {
        AuthCardDetails authCardDetails = getValidTestCard(null);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_REQUEST_WITHOUT_ADDRESS), actualRequest.getPayload());
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
                                anApplePayPaymentInfo()
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
        GooglePayPaymentInfo googlePayPaymentInfo = aGooglePayPaymentInfo().build();
        GooglePayAuthRequest validGooglePayData = new GooglePayAuthRequest(googlePayPaymentInfo, GOOGLE_PAY_ENCRYPTED_PAYMENT_DATA);

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
        GooglePayAuthRequest validGooglePay3dsData = new GooglePayAuthRequest(googlePayWalletPaymentInfoFor3ds, GOOGLE_PAY_ENCRYPTED_PAYMENT_DATA);

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
        GooglePayAuthRequest validGooglePay3dsData = new GooglePayAuthRequest(googlePayWalletPaymentInfoFor3ds, GOOGLE_PAY_ENCRYPTED_PAYMENT_DATA);

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
        GooglePayAuthRequest validGooglePay3dsData = new GooglePayAuthRequest(googlePayWalletPaymentInfoFor3ds, GOOGLE_PAY_ENCRYPTED_PAYMENT_DATA);

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

    @ParameterizedTest
    @CsvSource(useHeadersInDisplayName = true, textBlock = """
            corporateExemptionsEnabled, exemptionEngineEnabled, testTemplatePath, isCorporateCard
            true, false, templates/worldpay/valid-authorise-worldpay-3ds-request-corporate-exemption-authorisation.xml, true
            true, true, templates/worldpay/valid-authorise-worldpay-3ds-request-corporate-exemption-authorisation.xml, true
            false, true, templates/worldpay/valid-authorise-worldpay-3ds-request-exemption-optimised.xml, true
            true, true, templates/worldpay/valid-authorise-worldpay-3ds-request-exemption-optimised.xml, false
            """)
    void shouldGenerateValidAuthoriseOrderRequestAndIncludeCorrectExemptionResponse(Boolean corporateExemptionsEnabled,
                                                                                    Boolean exemptionEngineEnabled,
                                                                                    String testTemplatePath,
                                                                                    Boolean isCorporateCard) throws Exception {

        Address minAddress = new Address("123 My Street", null, "SW8URR", "London", null, "GB");

        AuthCardDetails authCorporateCardDetails = getValidTestCard(minAddress);
        authCorporateCardDetails.setCorporateCard(isCorporateCard);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId(WorldpayAuthoriseOrderSessionId.of("uniqueSessionId"))
                .with3dsRequired(true)
                .withCorporateExemptionEnabled(corporateExemptionsEnabled)
                .withExemptionEngine(exemptionEngineEnabled)
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withAuthorisationDetails(authCorporateCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(testTemplatePath), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
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
