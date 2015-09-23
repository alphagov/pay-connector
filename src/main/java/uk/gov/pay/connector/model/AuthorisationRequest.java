package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.Card;

public class AuthorisationRequest {
    private Card card;
    private String amount;
    private String description;

    public AuthorisationRequest(Card card, String amount, String description) {
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
}
