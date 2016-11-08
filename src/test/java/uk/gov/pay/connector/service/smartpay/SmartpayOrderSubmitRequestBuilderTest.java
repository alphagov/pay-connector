package uk.gov.pay.connector.service.smartpay;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.AddressEntity;
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
        AddressEntity addressEntity = AddressEntity.anAddress();
        addressEntity.setLine1("41");
        addressEntity.setLine2("Scala Street");
        addressEntity.setCity("London");
        addressEntity.setCounty("London");
        addressEntity.setPostcode("EC2A 1AE");
        addressEntity.setCountry("GB");

        Card card = buildCardDetails("Mr. Payment", "5555444433331111", "737", "08/18", "visa", addressEntity);

        String actualRequest = aSmartpayOrderSubmitRequest()
                .withMerchantCode("MerchantAccount")
                .withTransactionId("MyTransactionId")
                .withDescription("MyDescription")
                .withPaymentPlatformReference("MyPlatformReference")
                .withAmount("2000")
                .withCard(card)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload("valid-order-submit-smartpay-request.xml"), actualRequest);
    }

    private String expectedOrderSubmitPayload(final String expectedTemplate) throws IOException {
        return Resources.toString(getResource("templates/smartpay/" + expectedTemplate), Charset.defaultCharset());
    }
}
