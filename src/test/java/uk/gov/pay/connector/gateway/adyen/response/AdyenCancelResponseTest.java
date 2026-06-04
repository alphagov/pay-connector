package uk.gov.pay.connector.gateway.adyen.response;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.adyen.response.json.CancelResponseBody;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AdyenCancelResponseTest {

    @Test
    void should_map_Adyen_paymentPspReference_as_Pay_transactionId() {
        var cancelResponseBody = new CancelResponseBody("a-payment-psp-reference");

        BaseCancelResponse mappedCancelResponse = AdyenCancelResponse.from(cancelResponseBody);

        assertThat(mappedCancelResponse.getTransactionId(), is("a-payment-psp-reference"));
    }
}
