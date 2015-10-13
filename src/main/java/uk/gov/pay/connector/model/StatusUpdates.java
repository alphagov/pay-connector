package uk.gov.pay.connector.model;

import javafx.util.Pair;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.List;
import java.util.function.BiConsumer;

import static java.util.Collections.emptyList;

public class StatusUpdates {
    private final List<Pair<String, ChargeStatus>> newStatuses;
    private String providerResponse;

    public static StatusUpdates withUpdate(String providerResponse, List<Pair<String, ChargeStatus>> newStatuses) {
        return new StatusUpdates(providerResponse, newStatuses);
    }

    public static StatusUpdates noUpdate(String providerResponse) {
        return new StatusUpdates(providerResponse, emptyList());
    }

    public StatusUpdates(String providerResponse, List<Pair<String, ChargeStatus>> newStatuses) {
        this.providerResponse = providerResponse;
        this.newStatuses = newStatuses;
    }

    public String getResponseForProvider() {
        return providerResponse;
    }

    public void forEachStatusUpdate(BiConsumer<String, ChargeStatus> statusConsumer) {
        newStatuses.forEach(p -> statusConsumer.accept(p.getKey(), p.getValue()));
    }
}
