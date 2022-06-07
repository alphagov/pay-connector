package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.OrderRequestType;

import java.net.URI;
import java.util.Map;

public interface GatewayClientGetRequest {
    URI getUrl();
    Map<String, String> getHeaders();
    String getGatewayAccountType();
    PaymentGatewayName getPaymentProvider();
    OrderRequestType getOrderRequestType();
    Map<String, String> getQueryParams();
}
