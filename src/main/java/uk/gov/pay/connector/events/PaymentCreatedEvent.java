package uk.gov.pay.connector.events;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.PaymentCreatedEventDetails;

import java.time.ZonedDateTime;

public class PaymentCreatedEvent extends PaymentEvent<PaymentCreatedEventDetails> {

    private final ChargeEntity charge;

    private PaymentCreatedEvent(ChargeEntity charge) {
        this.charge = charge;
    }

    public static PaymentCreatedEvent from(ChargeEntity charge) {
        return new PaymentCreatedEvent(charge);
    }

    @Override
    public String getEventType() {
        return "PaymentCreated";
    }

    @Override
    public String getResourceExternalId() {
        return charge.getExternalId();
    }

    @Override
    public PaymentCreatedEventDetails getEventDetails() {
        return PaymentCreatedEventDetails.from(charge);
    }

    @Override
    public ZonedDateTime getTimestamp() {
        return charge.getCreatedDate();
    }
}
