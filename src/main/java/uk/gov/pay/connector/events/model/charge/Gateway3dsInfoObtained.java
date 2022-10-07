package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.charge.Gateway3dsInfoObtainedEventDetails;

import java.time.Instant;

public class Gateway3dsInfoObtained extends PaymentEvent {

    public Gateway3dsInfoObtained(String serviceId, boolean live, String resourceExternalId,
                                  Gateway3dsInfoObtainedEventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static Gateway3dsInfoObtained from(ChargeEntity charge, Instant eventDate) {
        return new Gateway3dsInfoObtained(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getExternalId(),
                Gateway3dsInfoObtainedEventDetails.from(charge),
                eventDate);
    }
}
