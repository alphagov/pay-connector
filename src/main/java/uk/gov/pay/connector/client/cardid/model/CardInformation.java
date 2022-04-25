package uk.gov.pay.connector.client.cardid.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CardInformation {
    @JsonProperty("brand")
    private String brand;

    @JsonProperty("type")
    private String type;

    @JsonProperty("label")
    private String label;

    @JsonProperty("corporate")
    private boolean corporate;

    @JsonProperty("prepaid")
    private PayersCardPrepaidStatus prepaidStatus;
    
    public CardInformation() {
        // for Jackson deserialisation
    }

    public CardInformation(String brand, String type, String label, boolean corporate, PayersCardPrepaidStatus prepaidStatus) {
        this.brand = brand;
        this.type = type;
        this.label = label;
        this.corporate = corporate;
        this.prepaidStatus = prepaidStatus;
    }

    public String getBrand() {
        return brand;
    }

    public String getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCorporate() {
        return corporate;
    }

    public PayersCardPrepaidStatus getPrepaidStatus() {
        return prepaidStatus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardInformation that = (CardInformation) o;
        return corporate == that.corporate && Objects.equals(brand, that.brand) && Objects.equals(type, that.type) && Objects.equals(label, that.label) && prepaidStatus == that.prepaidStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(brand, type, label, corporate, prepaidStatus);
    }
}
