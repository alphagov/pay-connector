package uk.gov.pay.connector.model;

public class CardAuthorisationRequest {
    private Card card;
    private Session session;
    private Browser browser;
    private Amount amount;

    private String transactionId;
    private String description;

    public CardAuthorisationRequest(Card card, Session session, Browser browser, Amount amount, String transactionId, String description) {
        this.card = card;
        this.session = session;
        this.browser = browser;
        this.amount = amount;
        this.transactionId = transactionId;
        this.description = description;
    }

    public Card getCard() {
        return card;
    }

    public Session getSession() {
        return session;
    }

    public Browser getBrowser() {
        return browser;
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
