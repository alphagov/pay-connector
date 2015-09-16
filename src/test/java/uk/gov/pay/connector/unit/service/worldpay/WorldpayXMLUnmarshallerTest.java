package uk.gov.pay.connector.unit.service.worldpay;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.service.worldpay.WorldpayAuthorisationResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayCaptureResponse;
import uk.gov.pay.connector.service.worldpay.WorldpayXMLUnmarshaller;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class WorldpayXMLUnmarshallerTest {

    @Test
    public void shouldUnmarshallASuccessfulCaptureResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/capture-success-response.xml");
        WorldpayCaptureResponse response = WorldpayXMLUnmarshaller.unmarshall(successPayload, WorldpayCaptureResponse.class);
        assertThat(response.isCaptured(), is(true));
        assertThat(response.isError(), is(false));
    }


    @Test
    public void shouldUnmarshallAnErrorForCaptureResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/error-response.xml");
        WorldpayCaptureResponse response = WorldpayXMLUnmarshaller.unmarshall(successPayload, WorldpayCaptureResponse.class);
        assertThat(response.getErrorCode(), is("5"));
        assertThat(response.getErrorMessage(), is("Order has already been paid"));
    }


    @Test
    public void shouldUnmarshallASuccessfulAuthorisationResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/authorisation-success-response.xml");
        WorldpayAuthorisationResponse response = WorldpayXMLUnmarshaller.unmarshall(successPayload, WorldpayAuthorisationResponse.class);
        assertThat(response.getLastEvent(), is("AUTHORISED"));
        assertNull(response.getRefusedReturnCode());
        assertNull(response.getRefusedReturnCodeDescription());
        assertTrue(response.isAuthorised());
    }

    @Test
    public void shouldUnmarshallAFailedAuthorisationResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/authorisation-failed-response.xml");
        WorldpayAuthorisationResponse response = WorldpayXMLUnmarshaller.unmarshall(successPayload, WorldpayAuthorisationResponse.class);
        assertThat(response.getLastEvent(), is("REFUSED"));
        assertThat(response.getRefusedReturnCode(), is("5"));
        assertThat(response.getRefusedReturnCodeDescription(), is("REFUSED"));
        assertFalse(response.isAuthorised());
    }

    @Test
    public void shouldUnmarshallAnErrorForAuthorisationResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/error-response.xml");
        WorldpayAuthorisationResponse response = WorldpayXMLUnmarshaller.unmarshall(successPayload, WorldpayAuthorisationResponse.class);
        assertThat(response.getErrorCode(), is("5"));
        assertThat(response.getErrorMessage(), is("Order has already been paid"));
    }

    private String readPayload(String path) throws IOException {
        return Resources.toString(Resources.getResource(path), Charset.defaultCharset());
    }
}