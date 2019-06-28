package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

/**
 * Payment has moved to a success state where the request has been submitted
 *
 */
public class AuthorisationSucceededEvent extends PaymentEventWithoutDetails {
    public AuthorisationSucceededEvent(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
