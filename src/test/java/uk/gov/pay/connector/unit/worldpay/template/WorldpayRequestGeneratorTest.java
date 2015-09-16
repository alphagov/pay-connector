package uk.gov.pay.connector.unit.worldpay.template;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.model.Address;
import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.model.Browser;
import uk.gov.pay.connector.model.Card;
import uk.gov.pay.connector.model.Session;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static uk.gov.pay.connector.model.Address.anAddress;
import static uk.gov.pay.connector.model.Card.aCard;
import static uk.gov.pay.connector.worldpay.template.WorldpayRequestGenerator.anOrderSubmitRequest;

public class WorldpayRequestGeneratorTest {

    private String userAgentHeader = "Mozilla/5.0 (Windows; U; Windows NT 5.1;en-GB; rv:1.9.1.5) Gecko/20091102 Firefox/3.5.5 (.NET CLR 3.5.30729)";
    private String acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

    @Test
    public void shouldGenerateValidOrderSubmitPayloadForAddressWithMinimumFields() throws Exception {
        Card card = getValidTestCard(anAddress()
                .withLine1("123 My Street")
                .withZip("SW8URR")
                .withCity("London")
                .withCountry("GB"));

        Session session = new Session("123.123.123.123", "0215ui8ib1");
        Browser browser = new Browser(acceptHeader, userAgentHeader);
        Amount amount = new Amount("500");

        String actualRequest = anOrderSubmitRequest()
                .withMerchantCode("MERCHANTCODE")
                .withTransactionId("MyUniqueTransactionId!")
                .withDescription("This is mandatory")
                .withAmount(amount)
                .withSession(session)
                .withCard(card)
                .withBrowser(browser)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-order-submit-worldpay-request-min-address.xml"), actualRequest);
    }

    @Test
    public void shouldGenerateValidOrderSubmitPayloadForAddressWithAllFields() throws Exception {

        Card card = getValidTestCard(anAddress()
                .withLine1("123 My Street")
                .withLine2("This road")
                .withLine3("Line 3")
                .withZip("SW8URR")
                .withCity("London")
                .withCounty("London county")
                .withCountry("GB"));
        Session session = new Session("123.123.123.123", "0215ui8ib1");
        Browser browser = new Browser(acceptHeader, userAgentHeader);
        Amount amount = new Amount("500");

        String actualRequest = anOrderSubmitRequest()
                .withMerchantCode("MERCHANTCODE")
                .withTransactionId("MyUniqueTransactionId!")
                .withDescription("This is mandatory")
                .withAmount(amount)
                .withSession(session)
                .withCard(card)
                .withBrowser(browser)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-order-submit-worldpay-request-full-address.xml"), actualRequest);

    }

    private Card getValidTestCard(Address address) {

        return aCard()
                .withCardDetails("Mr. Payment", "4111111111111111", "123", "12/15")
                .withAddress(address);

    }

    private String expectedOrderSubmitPayload(final String expectedTemplate) throws IOException {
        return Resources.toString(getResource("templates/worldpay/" + expectedTemplate), Charset.defaultCharset());
    }
}
