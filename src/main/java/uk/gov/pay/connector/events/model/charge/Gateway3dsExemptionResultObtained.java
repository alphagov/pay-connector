package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.charge.Gateway3dsExemptionResultObtainedEventDetails;

import java.time.ZonedDateTime;

public class Gateway3dsExemptionResultObtained extends PaymentEvent {

    public Gateway3dsExemptionResultObtained(String resourceExternalId,
                                             Gateway3dsExemptionResultObtainedEventDetails eventDetails, ZonedDateTime timestamp) {
        super(resourceExternalId, eventDetails, timestamp);
    }

    public static Gateway3dsExemptionResultObtained from(ChargeEntity charge, ZonedDateTime eventDate) {
        return new Gateway3dsExemptionResultObtained(
                charge.getExternalId(),
                Gateway3dsExemptionResultObtainedEventDetails.from(charge),
                eventDate);
    }

}
