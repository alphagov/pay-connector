package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

/**
 * @TODO(sfount) should this be moved to getTitle/ getDescription methods?
 * Payment has been rejected
 * - action has been aborted or rejected, payment moves to final rejected state
 *
 * @TODO(sfount) proposed: salient - move to rejected
 * @TODO(sfoun) question: should at the very high level ledger code we move to this to generic 'Payment failed' event
 *              with a sub reason; we could still support the external events but keep the ledger process streamlined
 */
public class AuthorisationRejected extends PaymentEventWithoutDetails {
    public AuthorisationRejected(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
