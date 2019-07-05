package uk.gov.pay.connector.events;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.events.eventdetails.PaymentCreatedEventDetails;

import java.time.ZonedDateTime;

public class PaymentCreated extends PaymentEvent {

    public PaymentCreated(String resourceExternalId, PaymentCreatedEventDetails paymentCreatedEventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, paymentCreatedEventDetails, timestamp);
    }

    // @TODO(sfount) add factory to create event given
    //               - ChargeEvent
    //               - Event Class type
    // @TODO(sfount) make all events work magically with factory
    // @TODO(sfount) submit PR
    // @TODO(sfount) rebase feature branch with this PR
    // @TODO(sfount) instantiate event through factory after having polled state transition queue
    // @TODO(sfount) emit the event - profit
    public static PaymentCreated from(ChargeEventEntity event) {
        return new PaymentCreated(
                event.getChargeEntity().getExternalId(),
                PaymentCreatedEventDetails.from(event.getChargeEntity()),
                event.getUpdated()
        );
    }

    public static PaymentCreated from(ChargeEntity charge) {
        return new PaymentCreated(
                charge.getExternalId(),
                PaymentCreatedEventDetails.from(charge),
                charge.getCreatedDate()
        );
    }
}
