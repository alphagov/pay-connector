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
}
