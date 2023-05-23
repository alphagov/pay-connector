package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.GatewayRequires3dsAuthorisationEventDetails;

import java.time.Instant;

public class GatewayRequires3dsAuthorisation extends PaymentEvent {
    public GatewayRequires3dsAuthorisation(String serviceId, boolean live,
                                           Long gatewayAccountId, String resourceExternalId,
                                           GatewayRequires3dsAuthorisationEventDetails eventDetails,
                                           Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }

    public static GatewayRequires3dsAuthorisation from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();
        return new GatewayRequires3dsAuthorisation(charge.getServiceId(), charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(), charge.getExternalId(), GatewayRequires3dsAuthorisationEventDetails.from(charge),
                chargeEvent.getUpdated().toInstant());
    }
}
