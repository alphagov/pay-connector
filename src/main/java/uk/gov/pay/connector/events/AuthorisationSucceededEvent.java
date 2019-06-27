package uk.gov.pay.connector.events;

/**
 * @TODO(sfount) should this be moved to getTitle/ getDescription methods?
 * Payment has moved to a success state where the request has been submitted
 *
 * @TODO(sfount) proposed: not salient - replace with `PaymentInternalStateUpdatedEvent`
 *               (compliment of `PaymentDataUpdatedEvent`)
 */
public class AuthorisationSucceededEvent extends PaymentEvent {
    // universal event
    // card payments: authorised
}
