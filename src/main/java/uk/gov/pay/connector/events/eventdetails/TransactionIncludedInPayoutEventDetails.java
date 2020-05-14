package uk.gov.pay.connector.events.eventdetails;

import java.util.Objects;

public class TransactionIncludedInPayoutEventDetails extends EventDetails {
    private final String gatewayPayoutId;

    public TransactionIncludedInPayoutEventDetails(String gatewayPayoutId) {
        this.gatewayPayoutId = gatewayPayoutId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionIncludedInPayoutEventDetails that = (TransactionIncludedInPayoutEventDetails) o;
        return Objects.equals(gatewayPayoutId, that.gatewayPayoutId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gatewayPayoutId);
    }
}
