package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.Instant;

public class PaymentInstrumentEvent extends Event {
    private String serviceId;
    private Boolean live;
    
    public PaymentInstrumentEvent(String serviceId, boolean live, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(timestamp, resourceExternalId, eventDetails);
        this.serviceId = serviceId;
        this.live = live;
    }

    public PaymentInstrumentEvent(String serviceId, boolean live, String resourceExternalId, Instant timestamp) {
        super(timestamp, resourceExternalId);
        this.serviceId = serviceId;
        this.live = live;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.PAYMENT_INSTRUMENT;
    }

    public Boolean isLive() {
        return live;
    }

    public String getServiceId() {
        return serviceId;
    }

    @Override
    public String toString() {
        return "PaymentInstrument{" +
                "resourceExternalId='" + getResourceExternalId() + '\'' +
                ", eventDetails=" + getEventDetails() +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}
