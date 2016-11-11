package uk.gov.pay.connector.util;

import com.google.common.io.Resources;
import org.junit.Test;
import uk.gov.pay.connector.service.smartpay.SmartpayAuthorisationResponse;
import uk.gov.pay.connector.service.smartpay.SmartpayCancelResponse;
import uk.gov.pay.connector.service.smartpay.SmartpayCaptureResponse;
import uk.gov.pay.connector.service.smartpay.SmartpayRefundResponse;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

public class SmartpayXMLUnmarshallerTest {

    @Test
    public void shouldUnmarshallAnAuthorisationSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/smartpay/authorisation-success-response.xml");
        SmartpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayAuthorisationResponse.class);

        assertThat(response.isAuthorised(), is(true));
        assertThat(response.getTransactionId(), is("7914435254138158"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAnAuthoriseFailResponse() throws Exception {
        String successPayload = readPayload("templates/smartpay/authorisation-failed-response.xml");
        SmartpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayAuthorisationResponse.class);

        assertThat(response.isAuthorised(), is(false));
        assertThat(response.getTransactionId(), is("8814436101583280"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAnAuthoriseErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/smartpay/authorisation-error-response.xml");
        SmartpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(errorPayload, SmartpayAuthorisationResponse.class);

        assertThat(response.isAuthorised(), is(false));
        assertThat(response.getTransactionId(), nullValue());

        assertThat(response.getErrorCode(), is("soap:Server"));
        assertThat(response.getErrorMessage(), is("validation 167 Original pspReference required for this operation"));
    }

    @Test
    public void shouldUnmarshallACaptureSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/smartpay/capture-success-response.xml");
        SmartpayCaptureResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayCaptureResponse.class);

        assertThat(response.getTransactionId(), is("7914435254138159"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallACaptureErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/smartpay/capture-error-response.xml");
        SmartpayCaptureResponse response = XMLUnmarshaller.unmarshall(errorPayload, SmartpayCaptureResponse.class);

        assertThat(response.getTransactionId(), nullValue());
        assertThat(response.getErrorCode(), is("soap:Server"));
        assertThat(response.getErrorMessage(), is("validation 163 Original pspReference required for this operation"));
    }

    @Test
    public void shouldUnmarshallACancelSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/smartpay/cancel-success-response.xml");
        SmartpayCancelResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayCancelResponse.class);

        assertThat(response.getTransactionId(), is("7914435254138149"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallACancelErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/smartpay/cancel-error-response.xml");
        SmartpayCancelResponse response = XMLUnmarshaller.unmarshall(errorPayload, SmartpayCancelResponse.class);

        assertThat(response.getTransactionId(), nullValue());
        assertThat(response.getErrorCode(), is("soap:Server"));
        assertThat(response.getErrorMessage(), is("validation 168 Original pspReference required for this operation"));
    }

    @Test
    public void shouldUnmarshallARefundSuccessResponse() throws Exception {
        String successPayload = readPayload("templates/smartpay/refund-success-response.xml");
        SmartpayRefundResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayRefundResponse.class);

        assertThat(response.getReference(), is(notNullValue()));
        assertThat(response.getReference().get(), is("8514774917520978"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallARefundErrorResponse() throws Exception {
        String errorPayload = readPayload("templates/smartpay/refund-error-response.xml");
        SmartpayRefundResponse response = XMLUnmarshaller.unmarshall(errorPayload, SmartpayRefundResponse.class);

        assertThat(response.getReference(), is(notNullValue()));
        assertThat(response.getReference().isPresent(), is(false));
        assertThat(response.getErrorCode(), is("soap:Server"));
        assertThat(response.getErrorMessage(), is("security 901 Invalid Merchant Account"));
    }

    private String readPayload(String path) throws IOException {
        return Resources.toString(Resources.getResource(path), Charset.defaultCharset());
    }
}