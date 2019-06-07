package uk.gov.pay.connector.events;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

public abstract class PaymentEvent<T extends EventDetails> extends Event<T> {
    @Override
    public ResourceType getResourceType() {
        return ResourceType.PAYMENT;
    }
}
