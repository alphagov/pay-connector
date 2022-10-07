package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.charge.Gateway3dsExemptionResultObtainedEventDetails;

import java.time.Instant;

public class Gateway3dsExemptionResultObtained extends PaymentEvent {

    public Gateway3dsExemptionResultObtained(String serviceId, boolean live, String resourceExternalId,
                                             Gateway3dsExemptionResultObtainedEventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, eventDetails, timestamp);
    }

    public static Gateway3dsExemptionResultObtained from(ChargeEntity charge, Instant eventDate) {
        return new Gateway3dsExemptionResultObtained(
                charge.getServiceId(),
                charge.getGatewayAccount().isLive(),
                charge.getExternalId(),
                Gateway3dsExemptionResultObtainedEventDetails.from(charge),
                eventDate);
    }

}
