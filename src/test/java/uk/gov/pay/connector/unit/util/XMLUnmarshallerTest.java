package uk.gov.pay.connector.unit.util;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.service.smartpay.SmartpayAuthorisationResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayAuthorisationResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayCaptureResponse;
import uk.gov.pay.connector.util.XMLUnmarshaller;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class XMLUnmarshallerTest {

    @Test
    public void shouldUnmarshallASuccessful_WorldpayCaptureResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/capture-success-response.xml");
        WorldpayCaptureResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCaptureResponse.class);
        assertTrue(response.isCaptured());
    }

    @Test
    public void shouldUnmarshallASuccessful_SmartpayCaptureResponse() throws Exception {
        String successPayload = readPayload("templates/smartpay/authorisation-success-response.xml");
        SmartpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayAuthorisationResponse.class);
        assertTrue(response.isAuthorised());
        assertThat(response.getPspReference(), is("7914435254138158"));
    }

    @Test
    public void shouldUnmarshallAnError_ForSmartpayAuthoriseResponse() throws Exception {
        String successPayload = readPayload("templates/smartpay/authorisation-refused-response.xml");
        SmartpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayAuthorisationResponse.class);
        assertThat(response.getPspReference(), is("8814436101583280"));
        assertThat(response.getErrorMessage(), is("CVC Declined"));
    }

    @Test
    public void shouldUnmarshallAnError_ForWorldpayCaptureResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/error-response.xml");
        WorldpayCaptureResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCaptureResponse.class);
        assertThat(response.getErrorCode(), is("5"));
        assertThat(response.getErrorMessage(), is("Order has already been paid"));
    }


    @Test
    public void shouldUnmarshallASuccessful_WorldpayAuthorisationResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/authorisation-success-response.xml");
        WorldpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayAuthorisationResponse.class);
        assertThat(response.getLastEvent(), is("AUTHORISED"));
        assertNull(response.getRefusedReturnCode());
        assertNull(response.getRefusedReturnCodeDescription());
        assertTrue(response.isAuthorised());
    }

    @Test
    public void shouldUnmarshallAFailed_WorldpayAuthorisationResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/authorisation-failed-response.xml");
        WorldpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayAuthorisationResponse.class);
        assertThat(response.getLastEvent(), is("REFUSED"));
        assertThat(response.getRefusedReturnCode(), is("5"));
        assertThat(response.getRefusedReturnCodeDescription(), is("REFUSED"));
        assertFalse(response.isAuthorised());
    }

    @Test
    public void shouldUnmarshallAnError_ForWorldpayAuthorisationResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/error-response.xml");
        WorldpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayAuthorisationResponse.class);
        assertThat(response.getErrorCode(), is("5"));
        assertThat(response.getErrorMessage(), is("Order has already been paid"));
    }

    private String readPayload(String path) throws IOException {
        return Resources.toString(Resources.getResource(path), Charset.defaultCharset());
    }
}