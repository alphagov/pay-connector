package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gateway.GatewayOperation;

import java.util.Map;

public interface GatewayRequest {
    GatewayAccountEntity getGatewayAccount();

    GatewayOperation getRequestType();

    Map<String, String> getGatewayCredentials();
}
