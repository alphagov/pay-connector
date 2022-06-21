package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.eventdetails.dispute.DisputeCreatedEventDetails;

import java.time.ZonedDateTime;

public class DisputeCreated extends DisputeEvent{
    public DisputeCreated(String resourceExternalId, String parentResourceExternalId, String serviceId,
                          Boolean live, DisputeCreatedEventDetails eventDetails, ZonedDateTime disputeCreated) {
        super(resourceExternalId, parentResourceExternalId, serviceId, live, eventDetails, disputeCreated);
    }

    @Override
    public String toString() {
        return "DisputeCreated{" +
                "resourceExternalId=" + getResourceExternalId() +
                ", eventDetails=" + getEventDetails() +
                ", timestamp=" + getTimestamp() +
                ", parentResourceExternalId= " + getParentResourceExternalId() +
                ", serviceId=" + getServiceId() +
                ", live=" + getLive() +
                '}';
    }
}
