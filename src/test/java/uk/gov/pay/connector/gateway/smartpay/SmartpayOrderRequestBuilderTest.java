package uk.gov.pay.connector.gateway.smartpay;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.model.OrderRequestType;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.util.AuthUtils;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.gateway.smartpay.SmartpayOrderRequestBuilder.aSmartpayAuthoriseOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.smartpay.SmartpayOrderRequestBuilder.aSmartpayCancelOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.smartpay.SmartpayOrderRequestBuilder.aSmartpayCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.smartpay.SmartpayOrderRequestBuilder.aSmartpayRefundOrderRequestBuilder;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_SPECIAL_CHAR_VALID_AUTHORISE_SMARTPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_SPECIAL_CHAR_VALID_CAPTURE_SMARTPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.SMARTPAY_VALID_AUTHORISE_SMARTPAY_REQUEST_MINIMAL;
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
        Address address = Address.anAddress();
        address.setLine1("41");
        address.setLine2("Scala Street");
        address.setCity("London");
        address.setCounty("London");
        address.setPostcode("EC2A 1AE");
        address.setCountry("GB");

        AuthCardDetails authCardDetails = AuthUtils.buildAuthCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", address);

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
    public void shouldGenerateValidAuthoriseOrderRequestWithSpecialCharactersInUserInput() throws Exception {
        Address address = Address.anAddress();
        address.setLine1("41");
        address.setLine2("Scala & Haskell Rocks");
        address.setCity("London <!-- ");
        address.setCounty("London -->");
        address.setPostcode("EC2A 1AE");
        address.setCountry("GB");

        AuthCardDetails authCardDetails = AuthUtils.buildAuthCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", address);

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

        Address address = Address.anAddress();
        address.setLine1("41 Acacia Avenue");
        address.setCity("London");
        address.setPostcode("EC2A 1AE");
        address.setCountry("GB");

        AuthCardDetails authCardDetails = AuthUtils.buildAuthCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", address);

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
}
