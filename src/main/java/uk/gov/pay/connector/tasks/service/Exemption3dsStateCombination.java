package uk.gov.pay.connector.tasks.service;

import java.util.Objects;

public class Exemption3dsStateCombination {
    private final ConnectorExemption3dsRequestedState requestedState;
    private final Connector3dsExemptionResultState connectorState;
    private final LedgerExemptionState ledgerState;

    public Exemption3dsStateCombination(ConnectorExemption3dsRequestedState requestedState, Connector3dsExemptionResultState connectorState, LedgerExemptionState ledgerState) {
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
