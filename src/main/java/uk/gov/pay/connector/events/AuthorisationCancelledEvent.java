package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

// Semantically same as auth rejected
public class AuthorisationCancelledEvent extends PaymentEventWithoutDetails {
    public AuthorisationCancelledEvent(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
