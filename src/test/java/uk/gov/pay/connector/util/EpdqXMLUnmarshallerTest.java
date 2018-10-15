package uk.gov.pay.connector.util;

import org.junit.Test;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqAuthorisationResponse;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCancelResponse;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.util.XMLUnmarshaller;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.ERROR;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.REJECTED;
import static uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus.SUBMITTED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_FAILED_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_OTHER_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_WAITING_EXTERNAL_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_AUTHORISATION_WAITING_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.EPDQ_CANCEL_WAITING_RESPONSE;

public class EpdqXMLUnmarshallerTest {

    @Test
    public void shouldUnmarshallAnAuthorisationSuccessResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_SUCCESS_RESPONSE);
        EpdqAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, EpdqAuthorisationResponse.class);

        assertThat(response.authoriseStatus(), is(AUTHORISED));
        assertThat(response.getTransactionId(), is("3014644340"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAnAuthorisationWaitingExternalResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_WAITING_EXTERNAL_RESPONSE);
        EpdqAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, EpdqAuthorisationResponse.class);

        assertThat(response.authoriseStatus(), is(SUBMITTED));
        assertThat(response.getTransactionId(), is("3014644340"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAnAuthorisationWaitingResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_WAITING_RESPONSE);
        EpdqAuthorisationResponse response = XMLUnmarshaller.unmarshall(successPayload, EpdqAuthorisationResponse.class);

        assertThat(response.authoriseStatus(), is(SUBMITTED));
        assertThat(response.getTransactionId(), is("3014644340"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAnAuthorisationFailedResponse() throws Exception {
        String failPayload = TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_FAILED_RESPONSE);
        EpdqAuthorisationResponse response = XMLUnmarshaller.unmarshall(failPayload, EpdqAuthorisationResponse.class);

        assertThat(response.authoriseStatus(), is(REJECTED));
        assertThat(response.getTransactionId(), is("3014644340"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallAnAuthorisationErrorResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_ERROR_RESPONSE);
        EpdqAuthorisationResponse response = XMLUnmarshaller.unmarshall(errorPayload, EpdqAuthorisationResponse.class);

        assertThat(response.authoriseStatus(), is(ERROR));
        assertThat(response.getTransactionId(), is("0"));

        assertThat(response.getErrorCode(), is("50001111"));
        assertThat(response.getErrorMessage(), is("An error has occurred; please try again later. If you are the owner or the integrator of this website, please log into the  back office to see the details of the error."));
    }

    @Test
    public void shouldUnmarshallAnAuthorisationOtherResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(EPDQ_AUTHORISATION_OTHER_RESPONSE);
        EpdqAuthorisationResponse response = XMLUnmarshaller.unmarshall(errorPayload, EpdqAuthorisationResponse.class);

        assertThat(response.authoriseStatus(), is(ERROR));
        assertThat(response.getTransactionId(), is("3014644340"));

        assertThat(response.getErrorCode(), is("0"));
        assertThat(response.getErrorMessage(), is("!"));
    }

    @Test
    public void shouldUnmarshallACaptureSuccessResponse() throws Exception {
        String successPayload = TestTemplateResourceLoader.load(TestTemplateResourceLoader.EPDQ_CAPTURE_SUCCESS_RESPONSE);
        EpdqCaptureResponse response = XMLUnmarshaller.unmarshall(successPayload, EpdqCaptureResponse.class);

        assertThat(response.getTransactionId(), is("3014644340"));
        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallACaptureErrorResponse() throws Exception {
        String errorPayload = TestTemplateResourceLoader.load(TestTemplateResourceLoader.EPDQ_CAPTURE_ERROR_RESPONSE);
        EpdqCaptureResponse response = XMLUnmarshaller.unmarshall(errorPayload, EpdqCaptureResponse.class);

        assertThat(response.getTransactionId(), is("3014644340"));
        assertThat(response.getErrorCode(), is("50001127"));
        assertThat(response.getErrorMessage(), is("|this order is not authorized|"));
    }

    @Test
    public void shouldUnmarshallACancelSuccessResponse() throws Exception {
        String payload = TestTemplateResourceLoader.load(EPDQ_CANCEL_SUCCESS_RESPONSE);
        EpdqCancelResponse response = XMLUnmarshaller.unmarshall(payload, EpdqCancelResponse.class);

        assertThat(response.cancelStatus(), is(BaseCancelResponse.CancelStatus.CANCELLED));
        assertThat(response.getTransactionId(), is("3014644340"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallACancelWaitingResponse() throws Exception {
        String payload = TestTemplateResourceLoader.load(EPDQ_CANCEL_WAITING_RESPONSE);
        EpdqCancelResponse response = XMLUnmarshaller.unmarshall(payload, EpdqCancelResponse.class);

        assertThat(response.cancelStatus(), is(BaseCancelResponse.CancelStatus.SUBMITTED));
        assertThat(response.getTransactionId(), is("3014644340"));

        assertThat(response.getErrorCode(), is(nullValue()));
        assertThat(response.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void shouldUnmarshallACancelErrorResponse() throws Exception {
        String payload = TestTemplateResourceLoader.load(EPDQ_CANCEL_ERROR_RESPONSE);
        EpdqCancelResponse response = XMLUnmarshaller.unmarshall(payload, EpdqCancelResponse.class);

        assertThat(response.cancelStatus(), is(BaseCancelResponse.CancelStatus.ERROR));
        assertThat(response.getTransactionId(), is("3014644340"));

        assertThat(response.getErrorCode(), is("50001127"));
        assertThat(response.getErrorMessage(), is("|this order is not authorized|"));
    }
}
