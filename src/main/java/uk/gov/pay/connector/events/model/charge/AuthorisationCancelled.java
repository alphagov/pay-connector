package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

// Semantically same as auth rejected
public class AuthorisationCancelled extends PaymentEventWithoutDetails {
    public AuthorisationCancelled(String serviceId, boolean live, Long gatewayAccountInternalId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountInternalId, resourceExternalId, timestamp);
    }
}
