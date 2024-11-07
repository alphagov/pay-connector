package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.GatewayDoesNotRequires3dsAuthorisationEventDetails;

import java.time.Instant;

public class GatewayDoesNotRequires3dsAuthorisation extends PaymentEvent {
    public GatewayDoesNotRequires3dsAuthorisation(String serviceId, boolean live,
                                                  Long gatewayAccountId, String resourceExternalId,
                                                  GatewayDoesNotRequires3dsAuthorisationEventDetails eventDetails,
                                                  Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }

    public static GatewayDoesNotRequires3dsAuthorisation from(ChargeEntity charge, Instant eventDate) {
        return new GatewayDoesNotRequires3dsAuthorisation(charge.getServiceId(), charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(), charge.getExternalId(),
                GatewayDoesNotRequires3dsAuthorisationEventDetails.from(), eventDate);
    }
}
