package uk.gov.pay.connector.gateway.model;

import uk.gov.pay.connector.card.model.Auth3dsRequiredEntity;

public interface Gateway3dsRequiredParams {

    Auth3dsRequiredEntity toAuth3dsRequiredEntity();
}
