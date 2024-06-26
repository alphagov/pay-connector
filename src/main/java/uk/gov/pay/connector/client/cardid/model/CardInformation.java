package uk.gov.pay.connector.client.cardid.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CardInformation (
    @JsonProperty("brand")
    String brand,

    @JsonProperty("type") 
    CardidCardType type,

    @JsonProperty("label")
    String label,

    @JsonProperty("corporate")
    boolean corporate,

    @JsonProperty("prepaid")
    PayersCardPrepaidStatus prepaidStatus
){
    public boolean isCorporate() {
        return corporate;
    }
}
