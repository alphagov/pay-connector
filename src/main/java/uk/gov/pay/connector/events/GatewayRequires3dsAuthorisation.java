package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class GatewayRequires3dsAuthorisation extends PaymentEventWithoutDetails {
    public GatewayRequires3dsAuthorisation(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
