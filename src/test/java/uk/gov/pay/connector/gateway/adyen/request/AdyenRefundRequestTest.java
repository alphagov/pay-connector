package uk.gov.pay.connector.gateway.adyen.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonassert.JsonAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.request.json.RefundRequestPayload;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.REFUND;

class AdyenRefundRequestTest {

    public static final String TEST_URL = "/adyen_refund_url";
    public static final String MERCHANT_ID = "TEST_MERCHANT_ID";
    public static final String STORE_ID = "store-id-123";
    public static final String REFUND_EXTERNAL_ID = "refund-external-id-123";
    private JsonObjectMapper jsonObjectMapper;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        jsonObjectMapper = new JsonObjectMapper(objectMapper);
    }

    @Test
    void should_build_adyen_refund_request_correctly() {
        AdyenRefundRequest adyenRefundRequest = buildValidRefundRequest();

        assertThat(adyenRefundRequest.getUrl().toString(), is(TEST_URL));
        assertThat(adyenRefundRequest.getHeaders(), hasEntry("X-API-Key", "test-api-key"));
        assertThat(adyenRefundRequest.getGatewayAccountType(), is("test"));
        assertThat(adyenRefundRequest.getPaymentProvider(), is(ADYEN));
    }

    @Test
    void should_return_gateway_order_with_serialised_payload() {
        var request = buildValidRefundRequest();

        assertThat(request.getGatewayOrder().getOrderRequestType(), is(REFUND));
        assertThat(request.getGatewayOrder().getMediaType(), is(APPLICATION_JSON_TYPE));
        JsonAssert.with(request.getGatewayOrder().getPayload())
                .assertThat("$.merchantAccount", is(MERCHANT_ID))
                .assertThat("$.reference", is(REFUND_EXTERNAL_ID))
                .assertThat("$.amount.value", is(500))
                .assertThat("$.amount.currency", is("GBP"))
                .assertThat("$.store", is(STORE_ID));
    }

    private AdyenRefundRequest buildValidRefundRequest() {
        var refundPayload = new RefundRequestPayload(
                MERCHANT_ID,
                new Amount("GBP", 500L),
                REFUND_EXTERNAL_ID,
                STORE_ID
        );
        return new AdyenRefundRequest(
                URI.create(TEST_URL),
                Map.of("X-API-Key", "test-api-key"),
                refundPayload,
                "test",
                jsonObjectMapper);
    }
}
