package uk.gov.pay.connector.charge.model.domain;

import java.util.Optional;

public interface Nettable {
    Long getAmount();

    Optional<Long> getFeeAmount();

    Optional<Long> getCorporateSurcharge();

    default Optional<Long> getNetAmount() {
        return getFeeAmount().map(fee -> getAmount() + getCorporateSurcharge().orElse(0L) - fee);
    }
}
