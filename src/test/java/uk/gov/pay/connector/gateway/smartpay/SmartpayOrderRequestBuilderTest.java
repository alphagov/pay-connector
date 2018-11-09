package uk.gov.pay.connector.gateway.smartpay;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.gateway.smartpay.SmartpayOrderRequestBuilder.aSmartpay3dsRequiredOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.smartpay.SmartpayOrderRequestBuilder.aSmartpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.smartpay.SmartpayOrderRequestBuilder.aSmartpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.smartpay.SmartpayOrderRequestBuilder.aSmartpayCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.smartpay.SmartpayOrderRequestBuilder.aSmartpayRefundOrderRequestBuilder;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_SPECIAL_CHAR_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_SPECIAL_CHAR_VALID_AUTHORISE_SMARTPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_SPECIAL_CHAR_VALID_CAPTURE_SMARTPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST_MINIMAL;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST_WITHOUT_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST_MINIMAL;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST_WITHOUT_ADDRESS;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_CANCEL_SMARTPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_CAPTURE_SMARTPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_REFUND_SMARTPAY_REQUEST;


public class SmartpayOrderRequestBuilderTest {
    private boolean oldIgnoreWhitespace;

    @Before
    public void setup() {
        oldIgnoreWhitespace = XMLUnit.getIgnoreWhitespace();
        XMLUnit.setIgnoreWhitespace(true);
    }

    @After
    public void tearDown() {
        XMLUnit.setIgnoreWhitespace(oldIgnoreWhitespace);
    }

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestForAddressWithAllFields() throws Exception {
        Address address = new Address("41", "Scala Street", "EC2A 1AE", "London", "London", "GB");

        AuthCardDetails authCardDetails = getValidTestCard(address);

        GatewayOrder actualRequest = aSmartpayAuthoriseOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestWhenAddressIsMissing() throws Exception {
        AuthCardDetails authCardDetails = getValidTestCard(null);

        GatewayOrder actualRequest = aSmartpayAuthoriseOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST_WITHOUT_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestWithSpecialCharactersInUserInput() throws Exception {
        Address address = new Address("41", "Scala & Haskell Rocks", "EC2A 1AE", "London <!-- ", "London -->", "GB");

        AuthCardDetails authCardDetails = getValidTestCard(address);

        GatewayOrder actualRequest = aSmartpayAuthoriseOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription <? ")
                .withPaymentPlatformReference("MyPlatformReference &>? <")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_SPECIAL_CHAR_VALID_AUTHORISE_SMARTPAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestForAddressWithOptionalFieldsMissing() throws Exception {

        Address address = new Address("41 Acacia Avenue", null, "EC2A 1AE", "London", null, "GB");

        AuthCardDetails authCardDetails = getValidTestCard(address);

        GatewayOrder actualRequest = aSmartpayAuthoriseOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST_MINIMAL), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthorise3dsRequiredOrderRequestForAddressWithAllFields() throws Exception {
        Address address = new Address("41", "Scala Street", "EC2A 1AE", "London", "London", "GB");

        AuthCardDetails authCardDetails = getValidTestCard(address);

        GatewayOrder actualRequest = aSmartpay3dsRequiredOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_3DS, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthorise3dsRequiredOrderRequestWhenAddressIsMissing() throws Exception {
        AuthCardDetails authCardDetails = getValidTestCard(null);

        GatewayOrder actualRequest = aSmartpay3dsRequiredOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST_WITHOUT_ADDRESS), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_3DS, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthorise3dsRequiredOrderRequestWithSpecialCharactersInUserInput() throws Exception {
        Address address = new Address("41", "Scala & Haskell Rocks", "EC2A 1AE", "London <!-- ", "London -->", "GB");

        AuthCardDetails authCardDetails = getValidTestCard(address);

        GatewayOrder actualRequest = aSmartpay3dsRequiredOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription <? ")
                .withPaymentPlatformReference("MyPlatformReference &>? <")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_SPECIAL_CHAR_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_3DS, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthorise3dsRequiredOrderRequestForAddressWithOptionalFieldsMissing() throws Exception {

        Address address = new Address("41 Acacia Avenue", null, "EC2A 1AE", "London", null, "GB");

        AuthCardDetails authCardDetails = getValidTestCard(address);

        GatewayOrder actualRequest = aSmartpay3dsRequiredOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_VALID_AUTHORISE_SMARTPAY_3DS_REQUEST_MINIMAL), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_3DS, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCaptureOrderRequest() throws Exception {
        GatewayOrder actualRequest = aSmartpayCaptureOrderRequestBuilder()
                .withTransactionId("MyTransactionId")
                .withMerchantCode("MerchantAccount")
                .withAmount("2000")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_VALID_CAPTURE_SMARTPAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.CAPTURE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCaptureOrderRequestWithSpecialCharactersInStrings() throws Exception {
        GatewayOrder actualRequest = aSmartpayCaptureOrderRequestBuilder()
                .withTransactionId("MyTransactionId & <!-- >")
                .withMerchantCode("MerchantAccount")
                .withAmount("2000")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_SPECIAL_CHAR_VALID_CAPTURE_SMARTPAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.CAPTURE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCancelOrderRequest() throws Exception {
        GatewayOrder actualRequest = aSmartpayCancelOrderRequestBuilder()
                .withTransactionId("MyTransactionId")
                .withMerchantCode("MerchantAccount")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_VALID_CANCEL_SMARTPAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.CANCEL, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidRefundOrderRequest() throws Exception {
        GatewayOrder actualRequest = aSmartpayRefundOrderRequestBuilder()
                .withReference("Reference")
                .withAmount("200")
                .withTransactionId("MyTransactionId")
                .withMerchantCode("MerchantAccount")
                .build();

        assertXMLEqual(TestTemplateResourceLoader.load(SMARTPAY_VALID_REFUND_SMARTPAY_REQUEST), actualRequest.getPayload());
        assertEquals(OrderRequestType.REFUND, actualRequest.getOrderRequestType());
    }

    private AuthCardDetails getValidTestCard(Address address) {
        return AuthCardDetailsFixture.anAuthCardDetails()
                .withCardHolder("Mr. Payment")
                .withCardNo("5555444433331111")
                .withCvc("737")
                .withEndDate("08/18")
                .withCardBrand("visa")
                .withAddress(address)
                .build();
    }
}
