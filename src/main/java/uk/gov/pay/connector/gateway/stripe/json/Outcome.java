package uk.gov.pay.connector.gateway.stripe.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Outcome {
    @JsonProperty("network_status")
    private String networkStatus;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("seller_message")
    private String sellerMessage;

    @JsonProperty("type")
    private String type;

    public String getNetworkStatus() {
        return networkStatus;
    }

    public String getReason() {
        return reason;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getSellerMessage() {
        return sellerMessage;
    }

    public String getType() {
        return type;
    }
}
