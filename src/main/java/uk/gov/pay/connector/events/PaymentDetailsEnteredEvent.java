package uk.gov.pay.connector.events;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.PaymentDetailsEnteredEventDetails;

import java.time.ZonedDateTime;
import java.util.Objects;

public class PaymentDetailsEnteredEvent extends PaymentEvent {

    private String resourceExternalId;
    private PaymentDetailsEnteredEventDetails eventDetails;
    private ZonedDateTime timestamp;

    public PaymentDetailsEnteredEvent(String resourceExternalId, PaymentDetailsEnteredEventDetails eventDetails,
                                      ZonedDateTime timestamp) {

        this.resourceExternalId = resourceExternalId;
        this.eventDetails = eventDetails;
        this.timestamp = timestamp;
    }

    public static PaymentDetailsEnteredEvent from(ChargeEntity charge) {
        ZonedDateTime lastEventDate = charge.getEvents().stream()
                .map(ChargeEventEntity::getUpdated)
                .max(ZonedDateTime::compareTo)
                .orElse(null);

        return new PaymentDetailsEnteredEvent(
                charge.getExternalId(),
                PaymentDetailsEnteredEventDetails.from(charge),
                lastEventDate);
    }

    @Override
    public String getResourceExternalId() {
        return resourceExternalId;
    }

    @Override
    public String getEventType() {
        return "PAYMENT_DETAILS_EVENT";
    }

    @Override
    public PaymentDetailsEnteredEventDetails getEventDetails() {
        return eventDetails;
    }

    @Override
    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public String getTitle() { return "Payment details entered event"; }

    public String getDescription() { return "The event happens when the payment details are entered"; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentDetailsEnteredEvent that = (PaymentDetailsEnteredEvent) o;
        return resourceExternalId.equals(that.resourceExternalId) &&
                eventDetails.equals(that.eventDetails) &&
                timestamp.equals(that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceExternalId, eventDetails, timestamp);
    }
}
