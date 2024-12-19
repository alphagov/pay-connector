package uk.gov.pay.connector.events.eventdetails.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class Requested3dsExemptionEventDetails extends EventDetails {

    @JsonProperty("exemption_3ds_requested")
    private final String exemption3dsRequested;

    public Requested3dsExemptionEventDetails(Exemption3dsType requested3dsExemptionType) {
        this.exemption3dsRequested = requested3dsExemptionType.name();
    }

    public static Requested3dsExemptionEventDetails from(ChargeEntity charge) {
        return new Requested3dsExemptionEventDetails(charge.getExemption3dsRequested());
    }

    public String getExemption3dsRequested() {
        return exemption3dsRequested;
    }
}
