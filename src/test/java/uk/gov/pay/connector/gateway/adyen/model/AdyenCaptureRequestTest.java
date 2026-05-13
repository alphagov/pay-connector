package uk.gov.pay.connector.gateway.adyen.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonassert.JsonAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.adyen.model.json.Amount;
import uk.gov.pay.connector.gateway.adyen.model.json.Capture;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.CAPTURE;

class AdyenCaptureRequestTest {
    public static final String TEST_URL = "/adyen_capture";
    public static final String MERCHANT_ID = "TEST_MERCHANT_ID";
    private JsonObjectMapper jsonObjectMapper;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        jsonObjectMapper = new JsonObjectMapper(objectMapper);
    }

    @Test
    void should_create_GatewayOrder_with_serialised_payload() {
        var request = buildValidCaptureRequest();

        assertThat(request.getGatewayOrder().getOrderRequestType(), is(CAPTURE));
        assertThat(request.getGatewayOrder().getMediaType(), is(APPLICATION_JSON_TYPE));
        JsonAssert.with(request.getGatewayOrder().getPayload())
                .assertThat("$.amount.value", is(500))
                .assertThat("$.amount.currency", is("GBP"))
                .assertThat("$.merchantAccount", is(MERCHANT_ID));
    }

    private AdyenCaptureRequest buildValidCaptureRequest() {
        var capturePayload = new Capture(
                new Amount("GBP", 500L),
                MERCHANT_ID);
        return new AdyenCaptureRequest(
                URI.create(TEST_URL),
                Map.of("X-API-Key", "test-api-key"),
                capturePayload,
                "test",
                jsonObjectMapper);
    }
}
