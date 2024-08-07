package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.charge.Requested3dsExemptionEventDetails;

import java.time.Instant;

public class Requested3dsExemption extends PaymentEvent {

    public Requested3dsExemption(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId,
                                 Requested3dsExemptionEventDetails requested3DsExemptionEventDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, requested3DsExemptionEventDetails, timestamp);
    }

    public static Requested3dsExemption from(ChargeEntity charge, Instant eventDate) {
        return new Requested3dsExemption(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getGatewayAccount().getId(),
                charge.getExternalId(),
                Requested3dsExemptionEventDetails.from(charge),
                eventDate);
    }
}
