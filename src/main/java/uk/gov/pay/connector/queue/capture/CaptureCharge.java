package uk.gov.pay.connector.queue.capture;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CaptureCharge {

    @JsonProperty("chargeId")
    private String chargeId;

    public String getChargeId() {
        return chargeId;
    }
}
