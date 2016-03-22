package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

public interface GatewayRequest {
    GatewayAccountEntity getGatewayAccount();
}
