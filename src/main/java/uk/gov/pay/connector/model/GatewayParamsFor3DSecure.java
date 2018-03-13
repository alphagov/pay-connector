package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;

public interface GatewayParamsFor3DSecure {

    Auth3dsDetailsEntity toAuth3dsDetailsEntity();
}
