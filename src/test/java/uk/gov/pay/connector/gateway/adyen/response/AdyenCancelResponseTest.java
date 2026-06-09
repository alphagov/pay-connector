package uk.gov.pay.connector.gateway.adyen.response;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.adyen.response.json.CancelResponseBody;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse.CancelStatus;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AdyenCancelResponseTest {

    public static final String HTTP_RESPONSE_STATUS = "some-HTTP-response-status";
    public static final String ADYEN_ERROR_MESSAGE = "some-Adyen-error-message";
    public static final String ADYEN_ERROR_CODE = "some-Adyen-error-code";
    public static final String ADYEN_ERROR_TYPE = "some-Adyen-error-type";
    public static final String PSP_REFERENCE_OF_THE_PAYMENT = "a-PSP-reference-of-the-payment";

    @Test
    void should_map_transaction_ID_from_Adyen_response() {
        var cancelResponseBody = new CancelResponseBody(PSP_REFERENCE_OF_THE_PAYMENT);

        BaseCancelResponse mappedCancelResponse = AdyenCancelResponse.from(cancelResponseBody);

        assertThat(mappedCancelResponse.getTransactionId(), is(PSP_REFERENCE_OF_THE_PAYMENT));
    }

    @Test
    void should_map_error_code_error_message_and_transaction_ID_from_Adyen_error_response() {
        var adyenError = new AdyenError(
                HTTP_RESPONSE_STATUS,
                ADYEN_ERROR_MESSAGE,
                ADYEN_ERROR_CODE,
                ADYEN_ERROR_TYPE,
                PSP_REFERENCE_OF_THE_PAYMENT);

        BaseCancelResponse mappedCancelResponse = AdyenCancelResponse.from(adyenError);

        assertThat(mappedCancelResponse.getErrorMessage(), is(ADYEN_ERROR_MESSAGE));
        assertThat(mappedCancelResponse.getErrorCode(), is(ADYEN_ERROR_CODE));
        assertThat(mappedCancelResponse.getTransactionId(), is(PSP_REFERENCE_OF_THE_PAYMENT));
    }

    @Test
    void should_map_error_type_from_Adyen_error_response() {
        var adyenError = new AdyenError(
                HTTP_RESPONSE_STATUS,
                ADYEN_ERROR_MESSAGE,
                ADYEN_ERROR_CODE,
                ADYEN_ERROR_TYPE,
                PSP_REFERENCE_OF_THE_PAYMENT);

        AdyenCancelResponse mappedCancelResponse = AdyenCancelResponse.from(adyenError);

        assertThat(mappedCancelResponse.errorType(), is(ADYEN_ERROR_TYPE));
    }

    @Test
    void should_map_to_a_cancel_status_of_ERROR_from_Adyen_error_response() {
        var adyenError = new AdyenError(
                HTTP_RESPONSE_STATUS,
                ADYEN_ERROR_MESSAGE,
                ADYEN_ERROR_CODE,
                ADYEN_ERROR_TYPE,
                PSP_REFERENCE_OF_THE_PAYMENT);

        BaseCancelResponse mappedCancelResponse = AdyenCancelResponse.from(adyenError);

        assertThat(mappedCancelResponse.cancelStatus(), is(CancelStatus.ERROR));
    }
}
