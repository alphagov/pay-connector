package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

/**
 * Payment has been cancelled by a user
 *     @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/cancel")
 *
 */
public class UserCancelledEvent extends PaymentEventWithoutDetails {
    public UserCancelledEvent(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
