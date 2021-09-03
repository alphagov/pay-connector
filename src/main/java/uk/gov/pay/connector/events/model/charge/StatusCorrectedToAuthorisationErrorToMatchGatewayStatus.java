package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class StatusCorrectedToAuthorisationErrorToMatchGatewayStatus extends PaymentEventWithoutDetails {
    public StatusCorrectedToAuthorisationErrorToMatchGatewayStatus(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
