package uk.gov.pay.connector.util;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.service.smartpay.SmartpayAuthorisationResponse;
import uk.gov.pay.connector.service.worldpay.*;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class XMLUnmarshallerTest {

    @Test
    public void shouldUnmarshallA_WorldpayCancelSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/cancel-success-response.xml")
                .replace("{{transactionId}}", "MyUniqueTransactionId!");

        WorldpayCancelResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCancelResponse.class);
        assertTrue(response.isCancelled());
    }


    @Test
    public void shouldUnmarshallA_WorldpayCancelErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/cancel-error-response.xml");

        WorldpayCancelResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayCancelResponse.class);
        assertFalse(response.isCancelled());
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    public void shouldUnmarshallA_WorldpayNotification() throws Exception {
        String transactionId = "MyUniqueTransactionId!";
        String status = "CAPTURED";
        String successPayload = readPayload("templates/worldpay/notification.xml")
                .replace("{{transactionId}}", transactionId)
                .replace("{{status}}", status);

        WorldpayNotification response = XMLUnmarshaller.unmarshall(successPayload, WorldpayNotification.class);
        assertThat(response.getStatus(), is(status));
        assertThat(response.getTransactionId(), is(transactionId));
        assertThat(response.getMerchantCode(), is("MERCHANTCODE"));
    }

    @Test
    public void shouldUnmarshallA_WorldpayInquirySuccessResponse() throws Exception {
        String transactionId = "MyUniqueTransactionId!";
        String status = "CAPTURED";

        String successPayload = readPayload("templates/worldpay/inquiry-success-response.xml")
                .replace("{{transactionId}}", transactionId)
                .replace("{{status}}", status);

        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderStatusResponse.class);
        assertFalse(response.isError());
        assertThat(response.getLastEvent(), is(status));
        assertThat(response.getTransactionId(), is(transactionId));
    }

    @Test
    public void shouldUnmarshallA_WorldpayInquiryErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/inquiry-error-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayOrderStatusResponse.class);
        assertTrue(response.isError());
        assertThat(response.getErrorCode(), is("5"));
        assertThat(response.getErrorMessage(), is("Could not find payment for order"));
    }

    @Test
    public void shouldUnmarshallA_WorldpayCaptureSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/capture-success-response.xml");
        WorldpayCaptureResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCaptureResponse.class);
        assertTrue(response.isCaptured());
    }

    @Test
    public void shouldUnmarshallA_ForWorldpayCaptureErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/capture-error-response.xml");
        WorldpayCaptureResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayCaptureResponse.class);
        assertFalse(response.isCaptured());
        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    public void shouldUnmarshallA_SmartpayCaptureSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/smartpay/authorisation-success-response.xml");
        SmartpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayAuthorisationResponse.class);
        assertTrue(response.isAuthorised());
        assertThat(response.getPspReference(), is("7914435254138158"));
    }

    @Test
    public void shouldUnmarshallAn_ForSmartpayAuthoriseErrorResponse() throws Exception {
        String successPayload = readPayload("templates/smartpay/authorisation-failed-response.xml");
        SmartpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayAuthorisationResponse.class);
        assertThat(response.getPspReference(), is("8814436101583280"));
        assertThat(response.getErrorMessage(), is("CVC Declined"));
    }

    @Test
    public void shouldUnmarshallA_WorldpayAuthorisationSuccecssResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/authorisation-success-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderStatusResponse.class);
        assertThat(response.getLastEvent(), is("AUTHORISED"));
        assertNull(response.getRefusedReturnCode());
        assertNull(response.getRefusedReturnCodeDescription());
        assertTrue(response.isAuthorised());
    }

    @Test
    public void shouldUnmarshallA_WorldpayAuthorisationFailedResponse() throws Exception {
        String failedPayload = readPayload("templates/worldpay/authorisation-failed-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(failedPayload, WorldpayOrderStatusResponse.class);
        assertThat(response.getLastEvent(), is("REFUSED"));
        assertThat(response.getRefusedReturnCode(), is("5"));
        assertThat(response.getRefusedReturnCodeDescription(), is("REFUSED"));
        assertFalse(response.isAuthorised());
    }

    @Test
    public void shouldUnmarshallA_WorldpayAuthorisationErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/authorisation-error-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayOrderStatusResponse.class);
        assertTrue(response.isError());
        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    public void shouldUnmarshallA_WorldpayRefundSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/refund-success-response.xml");
        WorldpayRefundResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayRefundResponse.class);
        assertTrue(response.isRefunded());
        assertNull(response.getErrorCode());
        assertNull(response.getErrorMessage());
    }

    @Test
    public void shouldUnmarshallA_WorldpayRefundErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/refund-error-response.xml");
        WorldpayRefundResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayRefundResponse.class);
        assertFalse(response.isRefunded());
        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    private String readPayload(String path) throws IOException {
        return Resources.toString(Resources.getResource(path), Charset.defaultCharset());
    }
}