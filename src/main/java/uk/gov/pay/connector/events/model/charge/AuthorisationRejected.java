package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

/**
 * Payment has been rejected
 * - action has been aborted or rejected, payment moves to final rejected state
 *
 */
public class AuthorisationRejected extends PaymentEventWithoutDetails {
    public AuthorisationRejected(String serviceId, boolean isLive, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, isLive, resourceExternalId, timestamp);
    }
}
