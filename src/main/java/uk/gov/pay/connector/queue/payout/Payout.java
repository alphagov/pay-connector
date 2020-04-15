package uk.gov.pay.connector.queue.payout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Payout {

    private String gatewayPayoutId;
    private String connectAccountId;

    public Payout() {
    }

    public Payout(String gatewayPayoutId, String connectAccountId) {
        this.gatewayPayoutId = gatewayPayoutId;
        this.connectAccountId = connectAccountId;
    }

    public String getGatewayPayoutId() {
        return gatewayPayoutId;
    }

    public String getConnectAccountId() {
        return connectAccountId;
    }
}
