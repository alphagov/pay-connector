package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import java.time.Instant;

public abstract class RefundEvent extends Event {
    private String serviceId;
    private Boolean live;
    private Long gatewayAccountInternalId;
    private String parentResourceExternalId;

    public RefundEvent(String serviceId, boolean live, Long gatewayAccountInternalId, String resourceExternalId, String parentResourceExternalId,
                       EventDetails eventDetails, Instant timestamp) {
        super(timestamp, resourceExternalId, eventDetails);
        this.parentResourceExternalId = parentResourceExternalId;
        this.serviceId = serviceId;
        this.live = live;
        this.gatewayAccountInternalId = gatewayAccountInternalId;
    }

    public RefundEvent(String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(timestamp, resourceExternalId, eventDetails);
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.REFUND;
    }

    public String getParentResourceExternalId() {
        return parentResourceExternalId;
    }

    public Boolean isLive() {
        return live;
    }

    public String getServiceId() {
        return serviceId;
    }

    public Long getGatewayAccountInternalId() {
        return gatewayAccountInternalId;
    }
}
