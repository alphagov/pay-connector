package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

/**
 * Payment has moved to a success state where the request has been submitted
 *
 */
public class AuthorisationSucceeded extends PaymentEventWithoutDetails {
    public AuthorisationSucceeded(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, timestamp);
    }
}
