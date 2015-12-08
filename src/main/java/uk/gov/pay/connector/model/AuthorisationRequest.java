package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.GatewayAccount;

public class AuthorisationRequest {
    private String chargeId;
    private Card card;
    private String amount;
    private String description;
    private GatewayAccount gatewayAccount;

    public AuthorisationRequest(String chargeId, Card card, String amount, String description, GatewayAccount gatewayAccount) {
        this.chargeId = chargeId;
        this.card = card;
        this.amount = amount;
        this.description = description;
        this.gatewayAccount = gatewayAccount;
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

    public GatewayAccount getGatewayAccount() {
        return gatewayAccount;
    }
}
