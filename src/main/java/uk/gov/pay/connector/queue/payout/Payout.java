package uk.gov.pay.connector.queue.payout;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.events.serializer.MicrosecondPrecisionDateTimeDeserializer;
import uk.gov.pay.connector.events.serializer.MicrosecondPrecisionDateTimeSerializer;

import java.time.ZonedDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class Payout {

    private String gatewayPayoutId;
    private String connectAccountId;

    @JsonSerialize(using = MicrosecondPrecisionDateTimeSerializer.class)
    @JsonDeserialize(using = MicrosecondPrecisionDateTimeDeserializer.class)
    private ZonedDateTime createdDate;

    public Payout() {
    }

    public Payout(String gatewayPayoutId, String connectAccountId, ZonedDateTime createdDate) {
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

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }
}
