package uk.gov.pay.connector.unit.worldpay.template;

import com.google.common.io.Resources;
import org.joda.time.DateTime;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static uk.gov.pay.connector.worldpay.template.WorldpayOrderCaptureRequestBuilder.anOrderCaptureRequest;

public class WorldpayOrderCaptureRequestGeneratorTest {

    @Test
    public void shouldGenerateValidOrderCapturePayload() throws Exception {


        DateTime date = new DateTime(2013, 2, 23, 0, 0);

        String actualRequest = anOrderCaptureRequest()
                .withMerchantCode("MERCHANTCODE")
                .withTransactionId("MyUniqueTransactionId!")
                .withAmount("500")
                .withDate(date)
                .build();

        assertXMLEqual(expectedOrderSubmitPayload(), actualRequest);
    }


    private String expectedOrderSubmitPayload() throws IOException {
        return Resources.toString(getResource("templates/worldpay/valid-capture-worldpay-request.xml"), Charset.defaultCharset());
    }
}