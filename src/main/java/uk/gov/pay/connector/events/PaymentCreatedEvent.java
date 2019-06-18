package uk.gov.pay.connector.events;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.PaymentCreatedEventDetails;

import java.time.ZonedDateTime;

import static uk.gov.pay.connector.events.MicrosecondPrecisionDateTimeSerializer.MICROSECOND_FORMATTER;

public class PaymentCreatedEvent extends PaymentEvent {

    private String resourceExternalId;
    private PaymentCreatedEventDetails eventDetails;
    private ZonedDateTime timestamp;

    public PaymentCreatedEvent(String resourceExternalId, PaymentCreatedEventDetails eventDetails, ZonedDateTime timestamp) {
        this.resourceExternalId = resourceExternalId;
        this.eventDetails = eventDetails;
        this.timestamp = timestamp;
    }

    public static PaymentCreatedEvent from(ChargeEntity charge) {
        return new PaymentCreatedEvent(
                charge.getExternalId(), 
                PaymentCreatedEventDetails.from(charge),
                charge.getCreatedDate());
    }

    @Override
    public String getEventType() {
        return "PaymentCreated";
    }

    @Override
    public String getResourceExternalId() {
        return resourceExternalId;
    }

    @Override
    public PaymentCreatedEventDetails getEventDetails() {
        return eventDetails;
    }

    @Override
    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "PaymentCreatedEvent{" +
                "resourceExternalId='" + resourceExternalId + '\'' +
                ", timestamp=" + MICROSECOND_FORMATTER.format(timestamp) +
                '}';
    }
}
