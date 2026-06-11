package uk.gov.pay.connector.gateway.adyen.request;

import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.adyen.request.json.Authorise3dsRequestPayload;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.AUTHORISE_3DS;

public record Adyen3dsAuthorisationRequest(
        URI url,
        Map<String, String> headers,
        String gatewayAccountType,
        Authorise3dsRequestPayload requestPayload,
        JsonObjectMapper jsonObjectMapper
) implements GatewayClientPostRequest {
    @Override
    public URI getUrl() {
        return url;
    }

    @Override
    public GatewayOrder getGatewayOrder() {
        var payload = jsonObjectMapper.objectToString(requestPayload);
        return new GatewayOrder(AUTHORISE_3DS, payload, APPLICATION_JSON_TYPE);
    }

    @Override
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String getGatewayAccountType() {
        return gatewayAccountType;
    }

    @Override
    public PaymentGatewayName getPaymentProvider() {
        return ADYEN;
    }
}
