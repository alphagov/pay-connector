package uk.gov.pay.connector.gateway.model;

import uk.gov.pay.connector.charge.model.domain.Auth3dsDetailsEntity;

public interface GatewayParamsFor3ds {

    Auth3dsDetailsEntity toAuth3dsDetailsEntity();
}
