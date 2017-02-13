package uk.gov.pay.connector.util;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.service.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.service.worldpay.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.LocalDate;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
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
                .replace("{{status}}", status)
                .replace("{{refund-ref}}", "REFUND-REF")
                .replace("{{bookingDateDay}}", "10")
                .replace("{{bookingDateMonth}}", "01")
                .replace("{{bookingDateYear}}", "2017");
        WorldpayNotification response = XMLUnmarshaller.unmarshall(successPayload, WorldpayNotification.class);
        assertThat(response.getStatus(), is(status));
        assertThat(response.getTransactionId(), is(transactionId));
        assertThat(response.getMerchantCode(), is("MERCHANTCODE"));
        assertThat(response.getReference(), is("REFUND-REF"));
        assertThat(response.getBookingDate(), is(LocalDate.parse("2017-01-10")));
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

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.AUTHORISED));
        assertThat(response.getTransactionId(), is("transaction-id"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshall3dsResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/3ds-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayOrderStatusResponse.class);

        assertNull(response.getLastEvent());
        assertNull(response.getRefusedReturnCode());
        assertNull(response.getRefusedReturnCodeDescription());
        assertNull(response.getErrorCode());
        assertNull(response.getErrorMessage());

        assertThat(response.get3dsPaRequest(), is("eJxVUsFuwjAM/ZWK80aSUgpFJogNpHEo2hjTzl"));
        assertThat(response.get3dsIssuerUrl(), is("https://secure-test.worldpay.com/jsp/test/shopper/ThreeDResponseSimulator.jsp"));

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.REQUIRES_3DS));
    }

    @Test
    public void shouldUnmarshallAAuthorisationFailedResponse() throws Exception {
        String failedPayload = readPayload("templates/worldpay/authorisation-failed-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(failedPayload, WorldpayOrderStatusResponse.class);
        assertThat(response.getLastEvent(), is("REFUSED"));
        assertThat(response.getRefusedReturnCode(), is("5"));
        assertThat(response.getRefusedReturnCodeDescription(), is("REFUSED"));

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.REJECTED));
        assertThat(response.getTransactionId(), is("MyUniqueTransactionId!12"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallCanceledAuthorisations() throws Exception {
        String failedPayload = readPayload("templates/worldpay/authorisation-cancelled-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(failedPayload, WorldpayOrderStatusResponse.class);
        assertThat(response.getLastEvent(), is("CANCELLED"));
        assertThat(response.getRefusedReturnCode(), is("5"));
        assertThat(response.getRefusedReturnCodeDescription(), is("CANCELLED"));

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.CANCELLED));
        assertThat(response.getTransactionId(), is("MyUniqueTransactionId!12"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAAuthorisationErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/authorisation-error-response.xml");
        WorldpayOrderStatusResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayOrderStatusResponse.class);

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.ERROR));
        assertThat(response.getTransactionId(), is(nullValue()));

        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    @Test
    public void shouldUnmarshallARefundSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/worldpay/refund-success-response.xml");
        WorldpayRefundResponse response = XMLUnmarshaller.unmarshall(successPayload, WorldpayRefundResponse.class);

        assertThat(response.getReference(), is(notNullValue()));
        assertThat(response.getReference().isPresent(), is(false));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallARefundErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/worldpay/refund-error-response.xml");
        WorldpayRefundResponse response = XMLUnmarshaller.unmarshall(errorPayload, WorldpayRefundResponse.class);

        assertThat(response.getReference(), is(notNullValue()));
        assertThat(response.getReference().isPresent(), is(false));
        assertThat(response.getErrorCode(), is("2"));
        assertThat(response.getErrorMessage(), is("Something went wrong."));
    }

    private String readPayload(String path) throws IOException {
        return Resources.toString(Resources.getResource(path), Charset.defaultCharset());
    }
}
