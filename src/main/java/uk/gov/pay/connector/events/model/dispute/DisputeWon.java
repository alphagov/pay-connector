package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.events.eventdetails.dispute.DisputeWonEventDetails;

import java.time.ZonedDateTime;

public class DisputeWon extends DisputeEvent {
    public DisputeWon(String resourceExternalId, String parentResourceExternalId, String serviceId,
                      Boolean live, DisputeWonEventDetails eventDetails, ZonedDateTime eventDate) {
        super(resourceExternalId, parentResourceExternalId, serviceId, live, eventDetails, eventDate);
    }

    @Override
    public String toString() {
        return "DisputeWon{" +
                "resourceExternalId=" + getResourceExternalId() +
                ", eventDetails=" + getEventDetails() +
                ", timestamp=" + getTimestamp() +
                ", parentResourceExternalId= " + getParentResourceExternalId() +
                ", serviceId=" + getServiceId() +
                ", live=" + getLive() +
                '}';
    }
}
