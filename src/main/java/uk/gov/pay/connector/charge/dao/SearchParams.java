package uk.gov.pay.connector.charge.dao;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchParams {

    private ZonedDateTime toDate;
    private Set<ChargeStatus> internalStates = new HashSet<>();

    public Set<ChargeStatus> getInternalStates() {
        return this.internalStates;
    }

    public SearchParams withInternalStates(List<ChargeStatus> statuses) {
        this.internalStates.addAll(statuses);
        return this;
    }

    public ZonedDateTime getToDate() {
        return toDate;
    }

    public SearchParams withToDate(ZonedDateTime toDate) {
        this.toDate = toDate;
        return this;
    }
}
