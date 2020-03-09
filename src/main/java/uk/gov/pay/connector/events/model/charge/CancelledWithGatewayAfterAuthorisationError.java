package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class CancelledWithGatewayAfterAuthorisationError extends PaymentEventWithoutDetails {
    public CancelledWithGatewayAfterAuthorisationError(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
