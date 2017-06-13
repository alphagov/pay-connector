package uk.gov.pay.connector.model.domain;

import java.util.Optional;

@FunctionalInterface
public interface DeferredStatusResolver {
  public Optional<InternalExternalStatus> resolve(ChargeEntity chargeEntity);
}
