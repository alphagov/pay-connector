package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.Amount;
import uk.gov.pay.connector.model.domain.Card;

public class AuthorisationRequest {
    private Card card;
    private Amount amount;
    private String description;

    public AuthorisationRequest(Card card, Amount amount, String description) {
        this.card = card;
        this.amount = amount;
        this.description = description;
    }

    public Card getCard() {
        return card;
    }

    public Amount getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }
}
