package uk.gov.pay.connector.gateway.adyen.request;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.DELETE_STORED_PAYMENT_DETAILS;

class AdyenDeleteStoredPaymentDetailsRequestTest {

    @Test
    void shouldReturnExpectedProperties() {
        URI url = URI.create("https://example.com/storedPaymentMethods/abc123");
        Map<String, String> headers = Map.of("X-API-Key", "a-test-key");
        Map<String, String> queryParams = Map.of(
                "merchantAccount", "merchant-account-test",
                "shopperReference", "agreement-external-id-123");
        String gatewayAccountType = "test";

        var request = new AdyenDeleteStoredPaymentDetailsRequest(url, headers, queryParams, gatewayAccountType);

        assertThat(request.getUrl(), is(url));
        assertThat(request.getHeaders(), is(headers));
        assertThat(request.getQueryParams(), is(queryParams));
        assertThat(request.getGatewayAccountType(), is(gatewayAccountType));
        assertThat(request.getOrderRequestType(), is(DELETE_STORED_PAYMENT_DETAILS));
        assertThat(request.getPaymentProvider(), is(ADYEN));
    }
}
