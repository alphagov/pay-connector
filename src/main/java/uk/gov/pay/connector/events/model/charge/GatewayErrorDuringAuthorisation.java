package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

/**
 * Payment has moved to a gateway error state during the authorisation process
 *
 */
public class GatewayErrorDuringAuthorisation extends PaymentEventWithoutDetails {
    public GatewayErrorDuringAuthorisation(String serviceId, boolean isLive, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, isLive, resourceExternalId, timestamp);
    }
}
