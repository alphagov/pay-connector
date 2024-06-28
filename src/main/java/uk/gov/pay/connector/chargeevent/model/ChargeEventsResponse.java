package uk.gov.pay.connector.chargeevent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChargeEventsResponse (
    @JsonProperty("charge_id")
    @Schema(example = "2c6vtn9pth38ppbmnt20d57t49")
    String chargeExternalId,
    
    List<TransactionEvent> events
){
    public static ChargeEventsResponse of(String chargeExternalId, List<TransactionEvent> events) {
        return new ChargeEventsResponse(chargeExternalId, events);
    }
}
