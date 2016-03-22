package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

/**
 * Created by ckalista on 22/03/2016.
 */
public interface GatewayRequest {
    GatewayAccountEntity getGatewayAccount();
}
