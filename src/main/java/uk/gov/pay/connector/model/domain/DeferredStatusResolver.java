package uk.gov.pay.connector.model.domain;

import java.util.Optional;

@FunctionalInterface
public interface DeferredStatusResolver {
  public Optional<Status> resolve(ChargeEntity chargeEntity);
}
