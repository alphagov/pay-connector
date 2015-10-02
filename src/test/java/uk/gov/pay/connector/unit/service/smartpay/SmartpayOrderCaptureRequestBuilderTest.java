package uk.gov.pay.connector.unit.service.smartpay;

import com.google.common.io.Resources;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static uk.gov.pay.connector.service.OrderCaptureRequestBuilder.aSmartpayOrderCaptureRequest;

public class SmartpayOrderCaptureRequestBuilderTest {

    @Test
    public void shouldGenerateValidOrderCapturePayload() throws Exception {
        String actualRequest = aSmartpayOrderCaptureRequest()
                .withMerchantCode("MerchantAccount")
                .withTransactionId("MyTransactionId")
                .withAmount("2000")
                .build();

        assertXMLEqual(expectedOrderSubmitPayload(), actualRequest);
    }

    private String expectedOrderSubmitPayload() throws IOException {
        return Resources.toString(getResource("templates/smartpay/valid-capture-smartpay-request.xml"), Charset.defaultCharset());
    }
}