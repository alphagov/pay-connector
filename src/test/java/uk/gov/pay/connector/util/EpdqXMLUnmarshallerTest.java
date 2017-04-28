package uk.gov.pay.connector.util;

import org.junit.Test;
import uk.gov.pay.connector.service.epdq.EpdqAuthorisationResponse;
import uk.gov.pay.connector.service.epdq.EpdqCaptureResponse;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.service.BaseAuthoriseResponse.AuthoriseStatus.*;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.*;

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
}
