package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;


/**
 * @TODO(sfount) should this be moved to getTitle/ getDescription methods?
 * Payment has been cancelled by an api call to
 * 
 *      @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/cancel") 
 * 
 * or possibly by ePDQ cancellation notification:
 *
 *      where the status they provide us in the notification is "6" but we need to know the current status of the 
 *      payment in order to decide whether it should transition to USER_CANCELLED, SYSTEM_CANCELLED or EXPIRED.
 *
 * (see commit 4d39870baa949e18b69383788486f422a78e1e61)
 * 
 * @TODO(sfount) proposed: salient - move to cancelled
 */
public class SystemCancelledEvent extends PaymentEventWithoutDetails {
    public SystemCancelledEvent(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
