package uk.gov.pay.connector.client.cardid.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CardInformationRequest(
        @JsonProperty("cardNumber")
        String cardNumber
) {
}
