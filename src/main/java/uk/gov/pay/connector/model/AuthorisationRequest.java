package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ServiceAccount;

public class AuthorisationRequest {
    private String chargeId;
    private Card card;
    private String amount;
    private String description;
    private ServiceAccount serviceAccount;

    public AuthorisationRequest(String chargeId, Card card, String amount, String description, ServiceAccount serviceAccount) {
        this.chargeId = chargeId;
        this.card = card;
        this.amount = amount;
        this.description = description;
        this.serviceAccount = serviceAccount;
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

    public ServiceAccount getServiceAccount() {
        return serviceAccount;
    }
}
