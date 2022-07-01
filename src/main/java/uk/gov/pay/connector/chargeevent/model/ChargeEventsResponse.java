package uk.gov.pay.connector.chargeevent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChargeEventsResponse {
    @JsonProperty("charge_id")
    @Schema(example = "2c6vtn9pth38ppbmnt20d57t49")
    private String chargeExternalId;
    private List<TransactionEvent> events;

    public ChargeEventsResponse(String chargeExternalId, List<TransactionEvent> events) {
        this.chargeExternalId = chargeExternalId;
        this.events = events;
    }

    public static ChargeEventsResponse of(String chargeExternalId, List<TransactionEvent> events) {
        return new ChargeEventsResponse(chargeExternalId, events);
    }

    public String getChargeExternalId() {
        return chargeExternalId;
    }

    public List<TransactionEvent> getEvents() {
        return events;
    }
}
