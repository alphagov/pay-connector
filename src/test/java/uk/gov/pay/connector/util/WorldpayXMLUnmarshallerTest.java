package uk.gov.pay.connector.util;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.service.worldpay.*;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class WorldpayXMLUnmarshallerTest {

    @Test
    public void shouldUnmarshallACancelSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/cancel-success-response.xml");
        WorldpayCancelResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCancelResponse.class);
        assertThat(response.getTransactionId(), is("transaction-id"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }


    @Test
    public void shouldUnmarshallACancelErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/cancel-error-response.xml");

        WorldpayCancelResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayCancelResponse.class);
        assertThat(response.getTransactionId(), is(nullValue()));
        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    public void shouldUnmarshallANotification() throws Exception {
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
    public void shouldUnmarshallACaptureSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/capture-success-response.xml");
        WorldpayCaptureResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayCaptureResponse.class);
        assertThat(response.getTransactionId(), is("transaction-id"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallACaptureErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/capture-error-response.xml");
        WorldpayCaptureResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayCaptureResponse.class);
        assertThat(response.getTransactionId(), is(nullValue()));
        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    public void shouldUnmarshallAAuthorisationSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/authorisation-success-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderStatusResponse.class);
        assertThat(response.getLastEvent(), is("AUTHORISED"));
        assertNull(response.getRefusedReturnCode());
        assertNull(response.getRefusedReturnCodeDescription());

        assertThat(response.isAuthorised(), is(true));
        assertThat(response.getTransactionId(), is("transaction-id"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAAuthorisationFailedResponse() throws Exception {
        String failedPayload = readPayload("templates/worldpay/authorisation-failed-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(failedPayload, WorldpayOrderStatusResponse.class);
        assertThat(response.getLastEvent(), is("REFUSED"));
        assertThat(response.getRefusedReturnCode(), is("5"));
        assertThat(response.getRefusedReturnCodeDescription(), is("REFUSED"));

        assertThat(response.isAuthorised(), is(false));
        assertThat(response.getTransactionId(), is("MyUniqueTransactionId!12"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAAuthorisationErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/authorisation-error-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayOrderStatusResponse.class);

        assertThat(response.isAuthorised(), is(false));
        assertThat(response.getTransactionId(), is(nullValue()));

        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    public void shouldUnmarshallARefundSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/refund-success-response.xml");
        WorldpayRefundResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayRefundResponse.class);

        assertThat(response.getTransactionId(), is("transaction-id"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallARefundErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/refund-error-response.xml");
        WorldpayRefundResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayRefundResponse.class);

        assertThat(response.getTransactionId(), is(nullValue()));
        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    private String readPayload(String path) throws IOException {
        return Resources.toString(Resources.getResource(path), Charset.defaultCharset());
    }
}
