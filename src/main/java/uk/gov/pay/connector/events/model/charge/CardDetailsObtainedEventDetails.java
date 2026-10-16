package uk.gov.pay.connector.events.model.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.service.payments.commons.model.CardExpiryDate;

public class CardDetailsObtainedEventDetails extends EventDetails {
    
    private final CardExpiryDate expiryDate;
    
    public CardDetailsObtainedEventDetails(CardExpiryDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public static CardDetailsObtainedEventDetails from(CardExpiryDate expiryDate) {
        return new CardDetailsObtainedEventDetails(expiryDate);
    }
    
    @JsonProperty("expiry_date")
    public String getExpiryDate() {
        return expiryDate.toString();
    }

}
