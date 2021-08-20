package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class GatewayRequires3dsAuthorisation extends PaymentEventWithoutDetails {
    public GatewayRequires3dsAuthorisation(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
