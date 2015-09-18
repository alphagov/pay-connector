package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.Amount;
import uk.gov.pay.connector.model.domain.Card;

public class AuthorisationRequest {
    private Card card;
    private Amount amount;
    private String transactionId;
    private String description;

    public AuthorisationRequest(Card card, Amount amount, String transactionId, String description) {
        this.card = card;
        this.amount = amount;
        this.transactionId = transactionId;
        this.description = description;
    }

    public Card getCard() {
        return card;
    }

    public Amount getAmount() {
        return amount;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getDescription() {
        return description;
    }
}
