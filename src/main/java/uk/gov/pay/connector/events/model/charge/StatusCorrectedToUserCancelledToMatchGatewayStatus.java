package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class StatusCorrectedToUserCancelledToMatchGatewayStatus extends PaymentEventWithoutDetails {
    public StatusCorrectedToUserCancelledToMatchGatewayStatus(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, timestamp);
    }
}
