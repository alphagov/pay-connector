package uk.gov.pay.connector.gateway.model;

import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;

public interface Gateway3dsRequiredParams {

    Auth3dsRequiredEntity toAuth3dsRequiredEntity();
}
