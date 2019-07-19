package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class UnexpectedGatewayErrorDuringAuthorisation extends PaymentEventWithoutDetails {
    public UnexpectedGatewayErrorDuringAuthorisation(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
