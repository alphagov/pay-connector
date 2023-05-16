package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

/**
 * Payment has moved to a success state where it has started
 *
 */
public class PaymentStarted extends PaymentEventWithoutDetails {
    public PaymentStarted(String serviceId, boolean live, Long gatewayAccountInternalId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountInternalId, resourceExternalId, timestamp);
    }
}
