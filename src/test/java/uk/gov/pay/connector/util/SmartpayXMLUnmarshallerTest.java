package uk.gov.pay.connector.util;

import org.junit.Test;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.smartpay.SmartpayAuthorisationResponse;
import uk.gov.pay.connector.gateway.smartpay.SmartpayCancelResponse;
import uk.gov.pay.connector.gateway.smartpay.SmartpayCaptureResponse;
import uk.gov.pay.connector.gateway.smartpay.SmartpayRefundResponse;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.*;

public class SmartpayXMLUnmarshallerTest {

    @Test
    public void shouldUnmarshallAnAuthorisationSuccessResponse() throws Exception {
        String transactionId = "7914435254138158";
        String successPayload = TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_SUCCESS_RESPONSE).replace("{{pspReference}}", transactionId);
        SmartpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayAuthorisationResponse.class);

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.AUTHORISED));
        assertThat(response.getTransactionId(), is(transactionId));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAnAuthoriseFailResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_FAILED_RESPONSE);
        SmartpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayAuthorisationResponse.class);

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.REJECTED));
        assertThat(response.getTransactionId(), is("8814436101583280"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAnAuthoriseErrorResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(SMARTPAY_AUTHORISATION_ERROR_RESPONSE);
        SmartpayAuthorisationResponse response = XMLUnmarshaller.unmarshall(errorPayload, SmartpayAuthorisationResponse.class);

        assertThat(response.authoriseStatus(), is(AuthoriseStatus.ERROR));
        assertThat(response.getTransactionId(), nullValue());

        assertThat(response.getErrorCode(), is("soap:Server"));
        assertThat(response.getErrorMessage(), is("validation 167 Original pspReference required for this operation"));
    }

    @Test
    public void shouldUnmarshallACaptureSuccessResponse() throws Exception {
        String transactionId = "7914435254138159";
        String successPayload = TestTemplateResourceLoader.load(SMARTPAY_CAPTURE_SUCCESS_RESPONSE).replace("{{pspReference}}", transactionId);
        SmartpayCaptureResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayCaptureResponse.class);

        assertThat(response.getTransactionId(), is(transactionId));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallACaptureErrorResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(SMARTPAY_CAPTURE_ERROR_RESPONSE);
        SmartpayCaptureResponse response = XMLUnmarshaller.unmarshall(errorPayload, SmartpayCaptureResponse.class);

        assertThat(response.getTransactionId(), nullValue());
        assertThat(response.getErrorCode(), is("soap:Server"));
        assertThat(response.getErrorMessage(), is("validation 163 Original pspReference required for this operation"));
    }

    @Test
    public void shouldUnmarshallACancelSuccessResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(SMARTPAY_CANCEL_SUCCESS_RESPONSE);
        SmartpayCancelResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayCancelResponse.class);

        assertThat(response.cancelStatus(), is(BaseCancelResponse.CancelStatus.CANCELLED));
        assertThat(response.getTransactionId(), is("7914435254138149"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallACancelErrorResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(SMARTPAY_CANCEL_ERROR_RESPONSE);
        SmartpayCancelResponse response = XMLUnmarshaller.unmarshall(errorPayload, SmartpayCancelResponse.class);

        assertThat(response.getTransactionId(), nullValue());
        assertThat(response.getErrorCode(), is("soap:Server"));
        assertThat(response.getErrorMessage(), is("validation 168 Original pspReference required for this operation"));
    }

    @Test
    public void shouldUnmarshallARefundSuccessResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(SMARTPAY_REFUND_SUCCESS_RESPONSE);
        SmartpayRefundResponse response = XMLUnmarshaller.unmarshall(successPayload, SmartpayRefundResponse.class);

        assertThat(response.getReference(), is(notNullValue()));
        assertThat(response.getReference().get(), is("8514774917520978"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallARefundErrorResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(SMARTPAY_REFUND_ERROR_RESPONSE);
        SmartpayRefundResponse response = XMLUnmarshaller.unmarshall(errorPayload, SmartpayRefundResponse.class);

        assertThat(response.getReference(), is(notNullValue()));
        assertThat(response.getReference().isPresent(), is(false));
        assertThat(response.getErrorCode(), is("soap:Server"));
        assertThat(response.getErrorMessage(), is("security 901 Invalid Merchant Account"));
    }
}
