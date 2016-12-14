package uk.gov.pay.connector.service.smartpay;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Card;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static uk.gov.pay.connector.service.OrderSubmitRequestBuilder.aSmartpayOrderSubmitRequest;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;

public class SmartpayOrderSubmitRequestBuilderTest {

    @Test
    public void shouldGenerateValidOrderSubmitPayloadForAddressWithAllFields() throws Exception {
        Address address = Address.anAddress();
        address.setLine1("41");
        address.setLine2("Scala Street");
        address.setCity("London");
        address.setCounty("London");
        address.setPostcode("EC2A 1AE");
        address.setCountry("GB");

        Card card = buildCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", address);

        String actualRequest = aSmartpayOrderSubmitRequest("authorise")
                .withMerchantCode("MerchantAccount")
                .withTransactionId("MyTransactionId")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("2000")
                .withCard(card)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-order-submit-smartpay-request.xml"), actualRequest);
    }

    @Test
    public void shouldGenerateValidOrderSubmitPayloadWithSpecialCharactersInUserInput() throws Exception {
        Address address = Address.anAddress();
        address.setLine1("41");
        address.setLine2("Scala & Haskell Rocks");
        address.setCity("London <!-- ");
        address.setCounty("London -->");
        address.setPostcode("EC2A 1AE");
        address.setCountry("GB");

        Card card = buildCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", address);

        String actualRequest = aSmartpayOrderSubmitRequest("authorise")
                .withMerchantCode("MerchantAccount")
                .withTransactionId("MyTransactionId")
                .withDescription("MyDescription <? ")
                .withPaymentPlatformReference("MyPlatformReference &>? <")
                .withAmount("2000")
                .withCard(card)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("special-char-valid-order-submit-smartpay-request.xml"), actualRequest);
    }

    private String expectedOrderSubmitPayload(final String expectedTemplate) throws IOException {
        return Resources.toString(getResource("templates/smartpay/" + expectedTemplate), Charset.defaultCharset());
    }
}
