package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.GatewayRequires3dsAuthorisationEventDetails;

import java.time.ZonedDateTime;

public class GatewayRequires3dsAuthorisation extends PaymentEvent {
    public GatewayRequires3dsAuthorisation(String serviceId, boolean live, String resourceExternalId,
                                           GatewayRequires3dsAuthorisationEventDetails eventDetails,
                                           ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static GatewayRequires3dsAuthorisation from(ChargeEventEntity chargeEvent) {
        ChargeEntity charge = chargeEvent.getChargeEntity();
        return new GatewayRequires3dsAuthorisation(charge.getServiceId(), charge.getGatewayAccount().isLive(),
                charge.getExternalId(), GatewayRequires3dsAuthorisationEventDetails.from(charge),
                chargeEvent.getUpdated());
    }
}
