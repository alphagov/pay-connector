package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class AuthorisationErrorCheckedWithGatewayChargeWasRejected extends PaymentEventWithoutDetails {
    public AuthorisationErrorCheckedWithGatewayChargeWasRejected(String serviceId, boolean isLive, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, isLive, resourceExternalId, timestamp);
    }
}
