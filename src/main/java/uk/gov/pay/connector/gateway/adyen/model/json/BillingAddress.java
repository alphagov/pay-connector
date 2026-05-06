package uk.gov.pay.connector.gateway.adyen.model.json;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BillingAddress(
        @JsonProperty("houseNumberOrName")
        String houseNumberOrName,
        @JsonProperty("street")
        String street,
        @JsonProperty("city")
        String city,
        @JsonProperty("country")
        String country,
        @JsonProperty("postalCode")
        String postalCode,
        @JsonProperty("stateOrProvince")
        String stateOrProvince) {
}
