package uk.gov.pay.connector.gateway.adyen.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.adyen.request.json.Authorise3dsRequestPayload;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE_3DS;

class Adyen3dsAuthorisationRequestTest {

    @Test
    void should_return_expected_values_from_accessors_and_gateway_order() throws Exception {
        var url = new URI("http://localhost:8080/payments/details");
        var headers = Map.of("X-Api-Key", "test-api-key");
        var gatewayAccountType = "test-account-type";
        var details = new Authorise3dsRequestPayload.Details("redirect-result-value");
        var payload = new Authorise3dsRequestPayload(details);
        var jsonObjectMapper = new JsonObjectMapper(new ObjectMapper());

        var request = new Adyen3dsAuthorisationRequest(
                url,
                headers,
                gatewayAccountType,
                payload,
                jsonObjectMapper
        );

        assertEquals(url, request.getUrl());
        assertEquals(headers, request.getHeaders());
        assertEquals(gatewayAccountType, request.getGatewayAccountType());
        assertEquals(ADYEN, request.getPaymentProvider());

        GatewayOrder gatewayOrder = request.getGatewayOrder();
        assertEquals(AUTHORISE_3DS, gatewayOrder.getOrderRequestType());
        assertEquals(APPLICATION_JSON_TYPE, gatewayOrder.getMediaType());

        String expectedPayload = jsonObjectMapper.objectToString(payload);
        assertEquals(expectedPayload, gatewayOrder.getPayload());
    }
}
