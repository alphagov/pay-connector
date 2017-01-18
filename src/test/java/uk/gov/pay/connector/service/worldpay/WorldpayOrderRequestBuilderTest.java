package uk.gov.pay.connector.service.worldpay;

import com.google.common.io.Resources;
import org.joda.time.DateTime;
import org.junit.Test;
import uk.gov.pay.connector.model.OrderRequestType;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.service.GatewayOrder;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderRequestBuilder.*;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;

public class WorldpayOrderRequestBuilderTest {

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestForAddressWithMinimumFields() throws Exception {

        Address minAddress = anAddress();
        minAddress.setLine1("123 My Street");
        minAddress.setPostcode("SW8URR");
        minAddress.setCity("London");
        minAddress.setCountry("GB");

        Card card = getValidTestCard(minAddress);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withCard(card)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-authorise-worldpay-request-min-address.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestForAddressWithAllFields() throws Exception {

        Address fullAddress = anAddress();
        fullAddress.setLine1("123 My Street");
        fullAddress.setLine2("This road");
        fullAddress.setPostcode("SW8URR");
        fullAddress.setCity("London");
        fullAddress.setCounty("London county");
        fullAddress.setCountry("GB");

        Card card = getValidTestCard(fullAddress);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withCard(card)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-authorise-worldpay-request-full-address.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestWhenSpecialCharactersInUserInput() throws Exception {

        Address address = anAddress();
        address.setLine1("123 & My Street");
        address.setLine2("This road -->");
        address.setPostcode("SW8 > URR");
        address.setCity("London !>");
        address.setCountry("GB");

        Card card = getValidTestCard(address);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description with <!-- ")
                .withAmount("500")
                .withCard(card)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("special-char-valid-authorise-worldpay-request-address.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCaptureOrderRequest() throws Exception {

        DateTime date = new DateTime(2013, 2, 23, 0, 0);

        GatewayOrder actualRequest = aWorldpayCaptureOrderRequestBuilder()
                .withDate(date)
                .withMerchantCode("MERCHANTCODE")
                .withAmount("500")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-capture-worldpay-request.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.CAPTURE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCaptureOrderRequestWithSpecialCharactersInStrings() throws Exception {

        DateTime date = new DateTime(2013, 2, 23, 0, 0);

        GatewayOrder actualRequest = aWorldpayCaptureOrderRequestBuilder()
                .withDate(date)
                .withMerchantCode("MERCHANTCODE")
                .withAmount("500")
                .withTransactionId("MyUniqueTransactionId <!-- & > ")
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("special-char-valid-capture-worldpay-request.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.CAPTURE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidCancelOrderRequest() throws Exception {

        GatewayOrder actualRequest = aWorldpayCancelOrderRequestBuilder()
                .withMerchantCode("MERCHANTCODE")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-cancel-worldpay-request.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.CANCEL, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidRefundOrderRequest() throws Exception {

        GatewayOrder actualRequest = aWorldpayRefundOrderRequestBuilder()
                .withReference("reference")
                .withAmount("200")
                .withMerchantCode("MERCHANTCODE")
                .withTransactionId("MyUniqueTransactionId!")
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-refund-worldpay-request.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.REFUND, actualRequest.getOrderRequestType());
    }

    private Card getValidTestCard(Address address) {
        return buildCardDetails("Mr. Payment", "4111111111111111", "123", "12/15", "visa", address);
    }

    private String expectedOrderSubmitPayload(final String expectedTemplate) throws IOException {
        return Resources.toString(getResource("templates/worldpay/" + expectedTemplate), Charset.defaultCharset());
    }
}
