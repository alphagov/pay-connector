package uk.gov.pay.connector.gateway.adyen.model;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.adyen.model.json.Amount;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdyenCaptureResponseTest {
    @Test
    void should_map_paymentPspReference_to_transactionId() {
        var captureResponse = aSuccessfulCaptureResponse();
        assertThat(captureResponse.getTransactionId(), is("gateway-transaction-id"));
    }

    @Test
    void should_map_errorCode_from_status_when_capture_is_not_successful() {
        var captureResponse = anUnsuccessfulCaptureResponse();
        assertThat(captureResponse.getErrorCode(), is("401"));
    }

    @Test
    void should_not_map_errorCode_from_status_when_capture_is_successful() {
        var captureResponse = aSuccessfulCaptureResponse();
        assertNull(captureResponse.getErrorCode());
    }

    @Test
    void should_map_message_to_errorMessage_if_present() {
        var captureResponse = anUnsuccessfulCaptureResponse();
        assertThat(captureResponse.getErrorMessage(), is("HTTP Status Response - Unauthorized"));
    }

    @Test
    void should_return_a_string_representation_of_AdyenCaptureResponse() {
        var captureResponse = aSuccessfulCaptureResponse();
        captureResponse.stringify();
        assertThat(captureResponse.stringify(), containsString("Adyen capture response(" +
                "merchantAccount: gov merchant account, " +
                "paymentPspReference: gateway-transaction-id, " +
                "pspReference: pspReferece-for-captured-payment, " +
                "status: received)"));
    }

    private static AdyenCaptureResponse aSuccessfulCaptureResponse() {
        return new AdyenCaptureResponse(
                "gov merchant account",
                "gateway-transaction-id",
                "pspReferece-for-captured-payment",
                "received",
                new Amount("GBP", 500L),
                null);
    }

    private static AdyenCaptureResponse anUnsuccessfulCaptureResponse() {
        return new AdyenCaptureResponse(
                null,
                null,
                null,
                "401",
                null,
                "HTTP Status Response - Unauthorized");
    }
}
