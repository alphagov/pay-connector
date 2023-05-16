package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.Instant;

public class PaymentEvent extends Event {
    private String serviceId;
    private Boolean live;
    private Long gatewayAccountId;
    
    public PaymentEvent(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(timestamp, resourceExternalId, eventDetails);
        this.serviceId = serviceId;
        this.live = live;
        this.gatewayAccountId = gatewayAccountId;
    }

    public PaymentEvent(String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(timestamp, resourceExternalId, eventDetails);
    }

    public PaymentEvent(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(timestamp, resourceExternalId);
        this.serviceId = serviceId;
        this.live = live;
        this.gatewayAccountId = gatewayAccountId;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.PAYMENT;
    }

    public Boolean isLive() {
        return live;
    }

    public String getServiceId() {
        return serviceId;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }
}
