package uk.gov.pay.connector.gateway.model.request;

import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.gateway.GatewayOperation;

public interface GatewayRequest {
    GatewayAccountEntity getGatewayAccount();

    GatewayOperation getRequestType();
}
