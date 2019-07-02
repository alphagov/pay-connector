package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

/**
 * @TODO(sfount) should this be moved to getTitle/ getDescription methods?
 * Payment has moved to an error state - this is usually because of a gateway error
 *
 * @TODO(sfount) salient - move to error state
 * @TODO(sfount) abstract to `PaymentFailed` ?
 */
public class GatewayErrorDuringAuthorisation extends PaymentEventWithoutDetails {
    public GatewayErrorDuringAuthorisation(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
