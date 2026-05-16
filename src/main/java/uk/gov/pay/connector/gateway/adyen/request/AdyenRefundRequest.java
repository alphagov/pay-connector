package uk.gov.pay.connector.gateway.adyen.request;

import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.adyen.request.json.RefundRequestPayload;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.REFUND;

public record AdyenRefundRequest(URI url,
                                 Map<String, String> headers,
                                 RefundRequestPayload payload,
                                 String gatewayAccountType,
                                 JsonObjectMapper jsonObjectMapper) implements GatewayClientPostRequest {
    @Override
    public URI getUrl() {
        return url;
    }

    @Override
    public GatewayOrder getGatewayOrder() {
        var requestPayload = jsonObjectMapper.objectToString(payload);
        return new GatewayOrder(REFUND, requestPayload, APPLICATION_JSON_TYPE);
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
