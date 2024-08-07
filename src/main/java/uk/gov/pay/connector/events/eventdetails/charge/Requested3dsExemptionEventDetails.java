package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Optional;

public class Requested3dsExemptionEventDetails extends EventDetails {

    private final String requested3dsExemptionType;

    public Requested3dsExemptionEventDetails(Exemption3dsType requested3dsExemptionType) {
        this.requested3dsExemptionType = requested3dsExemptionType.name();
    }

    public static Requested3dsExemptionEventDetails from(ChargeEntity charge) {
        return new Requested3dsExemptionEventDetails(charge.getExemption3dsRequested());
    }

    public String getExemption3dsRequestType() {
        return requested3dsExemptionType;
    }
}
