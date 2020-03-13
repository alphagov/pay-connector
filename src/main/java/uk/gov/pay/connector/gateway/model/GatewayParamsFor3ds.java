package uk.gov.pay.connector.gateway.model;

import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;

public interface GatewayParamsFor3ds {

    Auth3dsRequiredEntity toAuth3dsRequiredEntity();
}
