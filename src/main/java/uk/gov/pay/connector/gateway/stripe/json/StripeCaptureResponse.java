package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.gateway.CaptureResponse;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeCaptureResponse {
    @JsonProperty("id")
    private String id;

    @JsonProperty("card")
    private StripeCaptureResponse.Fee fee;

    public String getId() {
        return id;
    }

    public StripeCaptureResponse.Fee getFee() {
        return fee;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Fee {
        @JsonProperty("amount")
        private String amount;

        public String getAmount() {
            return amount;
        }
    }

    public static CaptureResponse toCaptureResponse(StripeCaptureResponse captureResponse, CaptureResponse.ChargeState state) {
        return new CaptureResponse(captureResponse.getId(), state, null, captureResponse.toString());
    }
}
