package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class StatusCorrectedToCapturedToMatchGatewayStatus extends PaymentEventWithoutDetails {
    public StatusCorrectedToCapturedToMatchGatewayStatus(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
