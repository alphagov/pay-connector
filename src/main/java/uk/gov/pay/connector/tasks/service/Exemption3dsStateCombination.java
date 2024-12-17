package uk.gov.pay.connector.tasks.service;

import java.util.Objects;

public class Exemption3dsStateCombination {
    private final ConnectExemption3dsRequestedState requestedState;
    private final ConnectExemption3dsState connectorState;
    private final LedgerExemptionState ledgerState;

    public Exemption3dsStateCombination(ConnectExemption3dsRequestedState requestedState, ConnectExemption3dsState connectorState, LedgerExemptionState ledgerState) {
        this.requestedState = requestedState;
        this.connectorState = connectorState;
        this.ledgerState = ledgerState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Exemption3dsStateCombination that = (Exemption3dsStateCombination) o;
        return requestedState == that.requestedState &&
                connectorState == that.connectorState &&
                ledgerState == that.ledgerState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestedState, connectorState, ledgerState);
    }
}
