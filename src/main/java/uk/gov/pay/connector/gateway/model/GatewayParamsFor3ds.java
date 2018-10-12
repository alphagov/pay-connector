package uk.gov.pay.connector.gateway.model;

import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;

public interface GatewayParamsFor3ds {

    Auth3dsDetailsEntity toAuth3dsDetailsEntity();
}
