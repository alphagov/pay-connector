package uk.gov.pay.connector.service.smartpay;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.model.OrderRequestType;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.service.GatewayOrder;
import uk.gov.pay.connector.util.CardUtils;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.service.smartpay.SmartpayOrderRequestBuilder.*;


public class SmartpayOrderRequestBuilderTest {

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestForAddressWithAllFields() throws Exception {
        Address address = Address.anAddress();
        address.setLine1("41");
        address.setLine2("Scala Street");
        address.setCity("London");
        address.setCounty("London");
        address.setPostcode("EC2A 1AE");
        address.setCountry("GB");

        AuthCardDetails authCardDetails = CardUtils.buildAuthCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", address);

        GatewayOrder actualRequest = aSmartpayAuthoriseOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-authorise-smartpay-request.xml"), actualRequest.getPayload());
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

        AuthCardDetails authCardDetails = CardUtils.buildAuthCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", address);

        GatewayOrder actualRequest = aSmartpayAuthoriseOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription <? ")
                .withPaymentPlatformReference("MyPlatformReference &>? <")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("special-char-valid-authorise-smartpay-request.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestForAddressWithOptionalFieldsMissing() throws Exception {

        Address address = Address.anAddress();
        address.setLine1("41");
        address.setCity("London");
        address.setPostcode("EC2A 1AE");
        address.setCountry("GB");

        AuthCardDetails authCardDetails = CardUtils.buildAuthCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", address);

        GatewayOrder actualRequest = aSmartpayAuthoriseOrderRequestBuilder()
                .withMerchantCode("MerchantAccount")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("2000")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-authorise-smartpay-request-minimal.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCaptureOrderRequest() throws Exception {
        GatewayOrder actualRequest = aSmartpayCaptureOrderRequestBuilder()
                .withTransactionId("MyTransactionId")
                .withMerchantCode("MerchantAccount")
                .withAmount("2000")
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-capture-smartpay-request.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.CAPTURE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCaptureOrderRequestWithSpecialCharactersInStrings() throws Exception {
        GatewayOrder actualRequest = aSmartpayCaptureOrderRequestBuilder()
                .withTransactionId("MyTransactionId & <!-- >")
                .withMerchantCode("MerchantAccount")
                .withAmount("2000")
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("special-char-valid-capture-smartpay-request.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.CAPTURE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCancelOrderRequest() throws Exception {
        GatewayOrder actualRequest = aSmartpayCancelOrderRequestBuilder()
                .withTransactionId("MyTransactionId")
                .withMerchantCode("MerchantAccount")
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-cancel-smartpay-request.xml"), actualRequest.getPayload());
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

        assertXMLEqual(expectedOrderSubmitPayload("valid-refund-smartpay-request.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.REFUND, actualRequest.getOrderRequestType());
    }

    private String expectedOrderSubmitPayload(final String expectedTemplate) throws IOException {
        return Resources.toString(getResource("templates/smartpay/" + expectedTemplate), Charset.defaultCharset());
    }
}
