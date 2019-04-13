package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.net.URI;
import java.util.Map;

public interface GatewayClientRequest {
    URI getUrl();
    GatewayOrder getGatewayOrder();
    Map<String, String> getHeaders();
    GatewayAccountEntity getGatewayAccount();
}
