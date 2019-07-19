package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

// Semantically same as auth rejected
public class AuthorisationCancelled extends PaymentEventWithoutDetails {
    public AuthorisationCancelled(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
