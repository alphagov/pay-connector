package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class GatewayRequires3dsAuthorisation extends PaymentEventWithoutDetails {
    public GatewayRequires3dsAuthorisation(String serviceId, boolean isLive, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, isLive, resourceExternalId, timestamp);
    }
}
