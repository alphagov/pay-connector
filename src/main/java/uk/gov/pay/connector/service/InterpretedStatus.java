package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.domain.Status;

import java.util.Optional;

public interface InterpretedStatus {

    default boolean isMapped() {
        return false;
    }

    default boolean isUnknown() {
        return false;
    }

    default boolean isIgnored() {
        return false;
    }

    default boolean isDeferred() { return false; }

    default Optional<Status> get() {
        return Optional.empty();
    }
}
