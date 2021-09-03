package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

/**
 * Payment has moved to a gateway error state during the authorisation process
 *
 */
public class GatewayErrorDuringAuthorisation extends PaymentEventWithoutDetails {
    public GatewayErrorDuringAuthorisation(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
