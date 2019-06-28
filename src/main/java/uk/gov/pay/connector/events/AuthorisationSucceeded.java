package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

/**
 * Payment has moved to a success state where the request has been submitted
 *
 */
public class AuthorisationSucceeded extends PaymentEventWithoutDetails {
    public AuthorisationSucceeded(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
