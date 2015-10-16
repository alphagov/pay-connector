package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.Card;

public class AuthorisationRequest {
    private String chargeId;
    private Card card;
    private String amount;
    private String description;

    public AuthorisationRequest(String chargeId, Card card, String amount, String description) {
        this.chargeId = chargeId;
        this.card = card;
        this.amount = amount;
        this.description = description;
    }

    public Card getCard() {
        return card;
    }

    public String getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getChargeId() {
        return chargeId;
    }
}
