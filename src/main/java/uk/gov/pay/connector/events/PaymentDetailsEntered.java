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
public class PaymentDetailsEntered extends PaymentEvent {

    public PaymentDetailsEntered(String resourceExternalId,
                                 PaymentDetailsEnteredEventDetails eventDetails,
                                 ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static PaymentDetailsEntered from(ChargeEntity charge) {
        ZonedDateTime lastEventDate = charge.getEvents().stream()
                .filter(e -> e.getStatus() == ChargeStatus.fromString(charge.getStatus()))
                .map(ChargeEventEntity::getUpdated)
                .max(ZonedDateTime::compareTo)
                .orElseThrow(() -> new ChargeEventNotFoundRuntimeException(charge.getExternalId()));

        return new PaymentDetailsEntered(
                charge.getExternalId(),
                PaymentDetailsEnteredEventDetails.from(charge),
                lastEventDate);
    }

    public static PaymentDetailsEntered from (ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();

        return new PaymentDetailsEntered(
                charge.getExternalId(),
                PaymentDetailsEnteredEventDetails.from(charge),
                chargeEvent.getUpdated()
        );
    }

    public String getTitle() { return "Payment details entered event"; }
    public String getDescription() { return "The event happens when the payment details are entered"; }
}
