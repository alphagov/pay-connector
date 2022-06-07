package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.PaymentGatewayName;

import java.net.URI;
import java.util.Map;

public interface GatewayClientPostRequest {
    URI getUrl();
    GatewayOrder getGatewayOrder();
    Map<String, String> getHeaders();
    String getGatewayAccountType();
    PaymentGatewayName getPaymentProvider();
}
