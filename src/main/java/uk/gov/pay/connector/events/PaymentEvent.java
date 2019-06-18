package uk.gov.pay.connector.events;

public abstract class PaymentEvent extends Event {
    @Override
    public ResourceType getResourceType() {
        return ResourceType.PAYMENT;
    }
}
