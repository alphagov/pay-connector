package uk.gov.pay.connector.queue.payout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.service.payments.commons.api.json.IsoInstantMicrosecondDeserializer;
import uk.gov.service.payments.commons.api.json.IsoInstantMicrosecondSerializer;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Payout {

    private String gatewayPayoutId;
    private String connectAccountId;

    @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
    @JsonDeserialize(using = IsoInstantMicrosecondDeserializer.class)
    private Instant createdDate;

    public Payout() {
    }

    public Payout(String gatewayPayoutId, String connectAccountId, Instant createdDate) {
        this.gatewayPayoutId = gatewayPayoutId;
        this.connectAccountId = connectAccountId;
        this.createdDate = createdDate;
    }

    public String getGatewayPayoutId() {
        return gatewayPayoutId;
    }

    public String getConnectAccountId() {
        return connectAccountId;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }
}
