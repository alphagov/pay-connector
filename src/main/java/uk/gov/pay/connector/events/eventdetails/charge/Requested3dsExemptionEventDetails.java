package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class Requested3dsExemptionEventDetails extends EventDetails {

    private final String type;

    public Requested3dsExemptionEventDetails(Exemption3dsType requested3dsExemptionType) {
        this.type = requested3dsExemptionType.name();
    }

    public static Requested3dsExemptionEventDetails from(ChargeEntity charge) {
        return new Requested3dsExemptionEventDetails(charge.getExemption3dsRequested());
    }

    public String getType() {
        return type;
    }
}
