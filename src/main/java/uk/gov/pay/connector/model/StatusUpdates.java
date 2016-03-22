package uk.gov.pay.connector.model;

import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;

public class StatusUpdates {
    private final List<Pair<String, ChargeStatus>> newStatuses;
    private final boolean succeeded;
    private String providerResponse;

    public static StatusUpdates withUpdate(String providerResponse, List<Pair<String, ChargeStatus>> newStatuses) {
        return new StatusUpdates(true, providerResponse, newStatuses);
    }

    public static StatusUpdates noUpdate(String providerResponse) {
        return new StatusUpdates(true, providerResponse, emptyList());
    }

    public static StatusUpdates failed() {
        return new StatusUpdates(false, "", Collections.emptyList());
    }

    public boolean successful() {
        return succeeded;
    }

    public StatusUpdates(boolean succeeded, String providerResponse, List<Pair<String, ChargeStatus>> newStatuses) {
        this.succeeded = succeeded;
        this.providerResponse = providerResponse;
        this.newStatuses = newStatuses;
    }

    public String getResponseForProvider() {
        return providerResponse;
    }

    public List<Pair<String, ChargeStatus>> getStatusUpdates() {
        return newStatuses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StatusUpdates that = (StatusUpdates) o;

        if (succeeded != that.succeeded) return false;
        if (newStatuses != null ? !newStatuses.equals(that.newStatuses) : that.newStatuses != null) return false;
        return providerResponse != null ? providerResponse.equals(that.providerResponse) : that.providerResponse == null;

    }

    @Override
    public int hashCode() {
        int result = newStatuses != null ? newStatuses.hashCode() : 0;
        result = 31 * result + (succeeded ? 1 : 0);
        result = 31 * result + (providerResponse != null ? providerResponse.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "StatusUpdates{" +
                "newStatuses=" + newStatuses +
                ", succeeded=" + succeeded +
                ", providerResponse='" + providerResponse + '\'' +
                '}';
    }
}
