package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

/**
 * Payment has been processed by the `ChargeExpiryProcess`, this can result in the payment
 * succeeding or failing to expire.
 */
public class PaymentExpired extends PaymentEventWithoutDetails {
    public PaymentExpired(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, timestamp);
    }
}
