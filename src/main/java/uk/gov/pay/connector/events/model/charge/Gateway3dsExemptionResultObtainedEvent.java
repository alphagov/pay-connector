package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.charge.Gateway3dsExemptionResultObtainedEventDetails;

import java.time.Instant;
import java.util.Objects;

public class Gateway3dsExemptionResultObtainedEvent extends PaymentEvent {

    public Gateway3dsExemptionResultObtainedEvent(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId,
                                                  Gateway3dsExemptionResultObtainedEventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }

    public static Gateway3dsExemptionResultObtainedEvent from(ChargeEntity charge, Instant eventDate) {
        return new Gateway3dsExemptionResultObtainedEvent(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                Gateway3dsExemptionResultObtainedEventDetails.from(charge),
                eventDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Gateway3dsExemptionResultObtainedEvent that = (Gateway3dsExemptionResultObtainedEvent) o;
        return  isLive() == that.isLive() &&
                Objects.equals(getServiceId(), that.getServiceId()) &&
                Objects.equals(getGatewayAccountId(), that.getGatewayAccountId()) &&
                Objects.equals(getResourceExternalId(), that.getResourceExternalId()) &&
                Objects.equals(getTimestamp(), that.getTimestamp()) &&
                Objects.equals(getEventDetails(), that.getEventDetails()) &&
                Objects.equals(getEventType(), that.getEventType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getServiceId(), isLive(), getGatewayAccountId(),
                getResourceExternalId(), getEventDetails(), getTimestamp(),
                getEventType());
    }
}
