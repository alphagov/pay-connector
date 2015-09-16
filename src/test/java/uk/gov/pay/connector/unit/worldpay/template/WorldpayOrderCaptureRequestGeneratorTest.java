package uk.gov.pay.connector.unit.worldpay.template;

import com.google.common.io.Resources;
import org.joda.time.DateTime;
import org.junit.Test;
import uk.gov.pay.connector.model.Amount;
import uk.gov.pay.connector.worldpay.template.WorldpayRequestGenerator;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;

public class WorldpayOrderCaptureRequestGeneratorTest {

    @Test
    public void shouldGenerateValidOrderCapturePayload() throws Exception {


        Amount amount = new Amount("500");
        DateTime date = new DateTime(2013, 2, 23, 0, 0);

        String actualRequest = WorldpayRequestGenerator.anOrderCaptureRequest()
                .withMerchantCode("MERCHANTCODE")
                .withTransactionId("MyUniqueTransactionId!")
                .withAmount(amount)
                .withDate(date)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload(), actualRequest);
    }


    private String expectedOrderSubmitPayload() throws IOException {
        return Resources.toString(getResource("templates/worldpay/valid-capture-worldpay-request.xml"), Charset.defaultCharset());
    }
}