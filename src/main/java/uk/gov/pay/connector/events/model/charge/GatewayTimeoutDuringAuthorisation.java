package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class GatewayTimeoutDuringAuthorisation extends PaymentEventWithoutDetails {
    public GatewayTimeoutDuringAuthorisation(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
