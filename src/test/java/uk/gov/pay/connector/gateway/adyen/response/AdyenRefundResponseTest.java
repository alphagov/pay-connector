package uk.gov.pay.connector.gateway.adyen.response;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenError;
import uk.gov.pay.connector.gateway.adyen.response.json.RefundResponseBody;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class AdyenRefundResponseTest {

    @Test
    void should_build_response_from_adyen_refund() {
        RefundResponseBody adyenRefund = new RefundResponseBody("merchant-account",
                "gateway-transaction-id",
                "refund-reference-from-psp",
                "refund-external-id",
                "received"
        );
        AdyenRefundResponse adyenRefundResponse = AdyenRefundResponse.from(adyenRefund);

        assertThat(adyenRefundResponse.getReference().get(), is("refund-reference-from-psp"));
        assertThat(adyenRefundResponse.stringify(),
                containsString("Adyen refund response(" +
                        "pspReference: refund-reference-from-psp, " +
                        "status: received)"));
    }

    @Test
    void should_build_response_from_adyen_error() {
        AdyenError adyenError = new AdyenError("401",
                "Not allowed",
                "010",
                "security",
                "psp-reference-123");

        AdyenRefundResponse adyenRefundResponse = AdyenRefundResponse.from(adyenError);

        assertThat(adyenRefundResponse.getReference().isPresent(), is(false));
        assertThat(adyenRefundResponse.getErrorCode(), is("010"));
        assertThat(adyenRefundResponse.getErrorMessage(), is("Not allowed"));

        assertThat(adyenRefundResponse.stringify(),
                containsString("Adyen refund response(" +
                        "errorCode: 010, " +
                        "errorType: security, " +
                        "errorMessage: Not allowed)"));
    }
}
