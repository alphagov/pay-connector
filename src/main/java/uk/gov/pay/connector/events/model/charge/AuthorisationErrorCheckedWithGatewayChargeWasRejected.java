package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class AuthorisationErrorCheckedWithGatewayChargeWasRejected extends PaymentEventWithoutDetails {
    public AuthorisationErrorCheckedWithGatewayChargeWasRejected(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
