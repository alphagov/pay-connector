package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

/**
 * Payment has moved to a gateway error state during the authorisation process
 *
 */
public class GatewayErrorDuringAuthorisation extends PaymentEventWithoutDetails {
    public GatewayErrorDuringAuthorisation(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, timestamp);
    }
}
