package uk.gov.pay.connector.events;

import uk.gov.pay.connector.events.eventpayload.EventPayload;

public abstract class PaymentEvent<T extends EventPayload> extends Event<T> {
    @Override
    public ResourceType getResourceType() {
        return ResourceType.PAYMENT;
    }
}
