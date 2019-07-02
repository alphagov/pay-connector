package uk.gov.pay.connector.events;

import uk.gov.pay.connector.charge.exception.ChargeEventNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.PaymentDetailsEnteredEventDetails;

import java.time.ZonedDateTime;

/**
 * Payment details entered by user, invoked through frontend endpoint
 * frontend: POST /frontend/charges/{chargeId}/status
 *
 */
public class PaymentDetailsEvent extends PaymentEvent {

    public PaymentDetailsEvent(String resourceExternalId,
                               PaymentDetailsEnteredEventDetails eventDetails,
                               ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentDetailsEvent from(ChargeEntity charge) {
        ZonedDateTime lastEventDate = charge.getEvents().stream()
                .filter(e -> e.getStatus() == ChargeStatus.fromString(charge.getStatus()))
                .map(ChargeEventEntity::getUpdated)
                .max(ZonedDateTime::compareTo)
                .orElseThrow(() -> new ChargeEventNotFoundRuntimeException(charge.getExternalId()));

        return new PaymentDetailsEvent(
                charge.getExternalId(),
                PaymentDetailsEnteredEventDetails.from(charge),
                lastEventDate);
    }

    public String getTitle() { return "Payment details entered event"; }
    public String getDescription() { return "The event happens when the payment details are entered"; }
}
