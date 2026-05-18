package uk.gov.pay.connector.gateway.adyen.model;

import jakarta.ws.rs.core.MediaType;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.adyen.model.json.PaymentCancelRequest;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.GatewayClientPostRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;

public record AdyenCancelRequest(URI url,
                                 Map<String, String> headers,
                                 PaymentCancelRequest paymentCancelRequest,
                                 String gatewayAccountType,
                                 JsonObjectMapper objectMapper) implements GatewayClientPostRequest {

    @Override
    public URI getUrl() {
        return url;
    }

    @Override
    public GatewayOrder getGatewayOrder() {
        var payload = objectMapper.objectToString(paymentCancelRequest);
        return new GatewayOrder(OrderRequestType.CANCEL, payload, MediaType.APPLICATION_JSON_TYPE);
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
