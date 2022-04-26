package uk.gov.pay.connector.client.cardid.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CardInformationRequest {
    
    @JsonProperty("cardNumber")
    private final String cardNumber;
    
    public CardInformationRequest(String cardNumber) {
        this.cardNumber = cardNumber;
    }
}
