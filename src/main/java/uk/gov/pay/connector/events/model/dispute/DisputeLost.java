package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.events.eventdetails.dispute.DisputeLostEventDetails;

import java.time.ZonedDateTime;

public class DisputeLost extends DisputeEvent {
    public DisputeLost(String resourceExternalId, String parentResourceExternalId, String serviceId, Boolean live,
                       DisputeLostEventDetails eventDetails, ZonedDateTime eventDate) {
        super(resourceExternalId, parentResourceExternalId, serviceId, live, eventDetails, eventDate);
    }

    @Override
    public String toString() {
        return "DisputeLost{" +
                "resourceExternalId=" + getResourceExternalId() +
                ", eventDetails=" + getEventDetails() +
                ", timestamp=" + getTimestamp() +
                ", parentResourceExternalId= " + getParentResourceExternalId() +
                ", serviceId=" + getServiceId() +
                ", live=" + getLive() +
                '}';
    }
}
