package uk.gov.pay.connector.service.worldpay;

import com.google.common.io.Resources;
import org.joda.time.DateTime;
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
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.service.worldpay.WorldpayOrderRequestBuilder.*;

public class WorldpayOrderRequestBuilderTest {

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestForAddressWithMinimumFields() throws Exception {

        Address minAddress = anAddress();
        minAddress.setLine1("123 My Street");
        minAddress.setPostcode("SW8URR");
        minAddress.setCity("London");
        minAddress.setCountry("GB");

        AuthCardDetails authCardDetails = getValidTestCard(minAddress);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId("uniqueSessionId")
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-authorise-worldpay-request-min-address.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuthoriseOrderRequestForAddressWithMinimumFieldsWhen3dsEnabled() throws Exception {

        Address minAddress = anAddress();
        minAddress.setLine1("123 My Street");
        minAddress.setPostcode("SW8URR");
        minAddress.setCity("London");
        minAddress.setCountry("GB");

        AuthCardDetails authCardDetails = getValidTestCard(minAddress);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId("uniqueSessionId")
                .with3dsRequired(true)
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-authorise-worldpay-3ds-request-min-address.xml"), actualRequest.getPayload());
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

        AuthCardDetails authCardDetails = getValidTestCard(fullAddress);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId("uniqueSessionId")
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
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

        AuthCardDetails authCardDetails = getValidTestCard(address);

        GatewayOrder actualRequest = aWorldpayAuthoriseOrderRequestBuilder()
                .withSessionId("uniqueSessionId")
                .withAcceptHeader("text/html")
                .withUserAgentHeader("Mozilla/5.0")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .withDescription("This is the description with <!-- ")
                .withAmount("500")
                .withAuthorisationDetails(authCardDetails)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("special-char-valid-authorise-worldpay-request-address.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE, actualRequest.getOrderRequestType());
    }

    @Test
    public void shouldGenerateValidAuth3dsResponseOrderRequest() throws Exception {

        GatewayOrder actualRequest = aWorldpay3dsResponseAuthOrderRequestBuilder()
                .withPaResponse3ds("I am an opaque 3D Secure PA response from the card issuer")
                .withSessionId("uniqueSessionId")
                .withTransactionId("MyUniqueTransactionId!")
                .withMerchantCode("MERCHANTCODE")
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-3ds-response-auth-worldpay-request.xml"), actualRequest.getPayload());
        assertEquals(OrderRequestType.AUTHORISE_3DS, actualRequest.getOrderRequestType());
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

    private AuthCardDetails getValidTestCard(Address address) {
        return CardUtils.buildAuthCardDetails("Mr. Payment", "4111111111111111", "123", "12/15", "visa", address);
    }

    private String expectedOrderSubmitPayload(final String expectedTemplate) throws IOException {
        return Resources.toString(getResource("templates/worldpay/" + expectedTemplate), Charset.defaultCharset());
    }
}
