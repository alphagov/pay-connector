package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class StatusCorrectedToAuthorisationErrorToMatchGatewayStatus extends PaymentEventWithoutDetails {
    public StatusCorrectedToAuthorisationErrorToMatchGatewayStatus(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
