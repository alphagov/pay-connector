package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

/**
 * Payment has been rejected
 * - action has been aborted or rejected, payment moves to final rejected state
 *
 */
public class AuthorisationRejected extends PaymentEventWithoutDetails {
    public AuthorisationRejected(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
