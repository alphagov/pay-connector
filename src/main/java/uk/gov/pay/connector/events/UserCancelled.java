package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

/**
 * Payment has been cancelled by a user
 *     @Path("/v1/api/accounts/{accountId}/charges/{chargeId}/cancel")
 *
 */
public class UserCancelled extends PaymentEventWithoutDetails {
    public UserCancelled(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
