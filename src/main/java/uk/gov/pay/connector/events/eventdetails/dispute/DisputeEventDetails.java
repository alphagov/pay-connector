package uk.gov.pay.connector.events.eventdetails.dispute;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Objects;

abstract class DisputeEventDetails extends EventDetails {
    protected final String gatewayAccountId;

    public DisputeEventDetails(String gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
    }

    public String getGatewayAccountId() {
        return gatewayAccountId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisputeEventDetails that = (DisputeEventDetails) o;
        return Objects.equals(gatewayAccountId, that.gatewayAccountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gatewayAccountId);
    }
}
