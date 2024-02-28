package uk.gov.pay.connector.events.eventdetails.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class Gateway3dsInfoObtainedEventDetails extends EventDetails {

    private String version3DS;

    public Gateway3dsInfoObtainedEventDetails(String version3DS) {
        this.version3DS = version3DS;
    }

    public static Gateway3dsInfoObtainedEventDetails from(ChargeEntity charge) {
        return new Gateway3dsInfoObtainedEventDetails(charge.getChargeCardDetails().get3dsRequiredDetails().getThreeDsVersion());
    }

    @JsonProperty("version_3ds")
    public String getVersion3DS() {
        return version3DS;
    }
}
