package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class StatusCorrectedToSystemCancelledToMatchGatewayStatus extends PaymentEventWithoutDetails {
    public StatusCorrectedToSystemCancelledToMatchGatewayStatus(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, timestamp);
    }
}
