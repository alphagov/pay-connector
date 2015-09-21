package uk.gov.pay.connector.unit.worldpay.template;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.Address;
import uk.gov.pay.connector.model.domain.Amount;
import uk.gov.pay.connector.model.domain.Card;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static uk.gov.pay.connector.model.domain.Address.anAddress;
import static uk.gov.pay.connector.util.CardUtils.buildCardDetails;
import static uk.gov.pay.connector.worldpay.template.WorldpayRequestGenerator.anOrderSubmitRequest;

public class WorldpayRequestGeneratorTest {
    @Test
    public void shouldGenerateValidOrderSubmitPayloadForAddressWithMinimumFields() throws Exception {

        Address minAddress = anAddress();
        minAddress.setLine1("123 My Street");
        minAddress.setPostcode("SW8URR");
        minAddress.setCity("London");
        minAddress.setCountry("GB");

        Card card = getValidTestCard(minAddress);
        Amount amount = new Amount("500");

        String actualRequest = anOrderSubmitRequest()
                .withMerchantCode("MERCHANTCODE")
                .withTransactionId("MyUniqueTransactionId!")
                .withDescription("This is the description")
                .withAmount(amount)
                .withCard(card)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-order-submit-worldpay-request-min-address.xml"), actualRequest);
    }

    @Test
    public void shouldGenerateValidOrderSubmitPayloadForAddressWithAllFields() throws Exception {

        Address fullAddress = anAddress();
        fullAddress.setLine1("123 My Street");
        fullAddress.setLine2("This road");
        fullAddress.setLine3("Line 3");
        fullAddress.setPostcode("SW8URR");
        fullAddress.setCity("London");
        fullAddress.setCounty("London county");
        fullAddress.setCountry("GB");

        Card card = getValidTestCard(fullAddress);
        Amount amount = new Amount("500");

        String actualRequest = anOrderSubmitRequest()
                .withMerchantCode("MERCHANTCODE")
                .withTransactionId("MyUniqueTransactionId!")
                .withDescription("This is the description")
                .withAmount(amount)
                .withCard(card)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-order-submit-worldpay-request-full-address.xml"), actualRequest);

    }

    private Card getValidTestCard(Address address) {
        return buildCardDetails("Mr. Payment", "4111111111111111", "123", "12/15", address);
    }

    private String expectedOrderSubmitPayload(final String expectedTemplate) throws IOException {
        return Resources.toString(getResource("templates/worldpay/" + expectedTemplate), Charset.defaultCharset());
    }
}
