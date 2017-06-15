package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.domain.ChargeStatus;

public interface StatusMapper<V> {

   InterpretedStatus from(V providerStatus, ChargeStatus currentStatus);

}
