package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

/**
 * Payment has moved to a success state where the request has been submitted
 *
 */
public class AuthorisationSucceeded extends PaymentEventWithoutDetails {
    public AuthorisationSucceeded(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
