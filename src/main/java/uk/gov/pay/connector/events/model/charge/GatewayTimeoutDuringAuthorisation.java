package uk.gov.pay.connector.events.model.charge;

import java.time.Instant;

public class GatewayTimeoutDuringAuthorisation extends PaymentEventWithoutDetails {
    public GatewayTimeoutDuringAuthorisation(String serviceId, boolean live, String resourceExternalId, Instant timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
