package uk.gov.pay.connector.gateway.adyen.request;

import com.jayway.jsonassert.JsonAssert;
import io.dropwizard.jackson.Jackson;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.adyen.request.json.CancelRequestPayload;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.CANCEL;

class AdyenCancelRequestTest {

    public static final URI TEST_URI = URI.create("https://example.com");
    public static final String GATEWAY_ACCOUNT_TYPE = "gateway-account-type";
    private static final Map<String, String> HEADERS = Map.of(
            "header1", "value1",
            "header2", "value2");
    public static final String REFERENCE = "a-reference";
    public static final String MERCHANT_ACCOUNT = "a-merchant-account";

    private AdyenCancelRequest request;

    @BeforeEach
    void createCancelRequest() {
        var cancelPayload = new CancelRequestPayload(REFERENCE, MERCHANT_ACCOUNT);
        var jsonObjectMapper = new JsonObjectMapper(Jackson.newObjectMapper());
        request = new AdyenCancelRequest(TEST_URI, HEADERS, cancelPayload, GATEWAY_ACCOUNT_TYPE, jsonObjectMapper);
    }

    @Test
    void should_return_expected_properties() {
        assertThat(request.getUrl(), is(TEST_URI));
        assertThat(request.getHeaders(), is(HEADERS));
        assertThat(request.getGatewayAccountType(), is(GATEWAY_ACCOUNT_TYPE));

    }

    @Test
    void should_return_the_Adyen_payment_provider() {
        assertThat(request.getPaymentProvider(), is(ADYEN));
    }

    @Test
    void should_return_CANCEL_type_gateway_order() {
        assertThat(request.getGatewayOrder().getOrderRequestType(), is(CANCEL));
    }

    @Test
    void should_return_gateway_order_with_serialised_PaymentCancelRequest_payload() {
        JsonAssert.with(request.getGatewayOrder().getPayload())
                .assertEquals("reference", REFERENCE)
                .assertEquals("merchantAccount", MERCHANT_ACCOUNT);
    }

    @Test
    void should_return_gateway_order_with_JSON_media_type() {
        assertThat(request.getGatewayOrder().getMediaType(), is(MediaType.APPLICATION_JSON_TYPE));
    }
}
