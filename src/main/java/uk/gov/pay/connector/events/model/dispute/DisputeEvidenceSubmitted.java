package uk.gov.pay.connector.events.model.dispute;

import uk.gov.pay.connector.events.eventdetails.dispute.DisputeEvidenceSubmittedEventDetails;

import java.time.ZonedDateTime;

public class DisputeEvidenceSubmitted extends DisputeEvent {
    public DisputeEvidenceSubmitted(String resourceExternalId, String parentResourceExternalId, String serviceId,
                                    Boolean live, DisputeEvidenceSubmittedEventDetails eventDetails,
                                    ZonedDateTime eventDate) {
        super(resourceExternalId, parentResourceExternalId, serviceId, live, eventDetails, eventDate);
    }

    @Override
    public String toString() {
        return "DisputeEvidenceSubmitted{" +
                "resourceExternalId=" + getResourceExternalId() +
                ", eventDetails=" + getEventDetails() +
                ", timestamp=" + getTimestamp() +
                ", parentResourceExternalId= " + getParentResourceExternalId() +
                ", serviceId=" + getServiceId() +
                ", live=" + getLive() +
                '}';
    }
}
