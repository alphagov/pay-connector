package uk.gov.pay.connector.service;

import java.util.NoSuchElementException;

public interface Status {

    default boolean isMapped() {
        return false;
    }

    default boolean isUnknown() {
        return false;
    }

    default boolean isIgnored() {
        return false;
    }

    default Enum get() {
        throw new NoSuchElementException("No status present");
    }
}
