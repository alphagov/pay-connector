package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccount;

public class AuthorisationRequest {
    private Card card;
    private ChargeEntity charge;

    public AuthorisationRequest(ChargeEntity charge, Card card) {
        this.charge = charge;
        this.card = card;
    }

    public Card getCard() {
        return card;
    }

    public String getAmount() {
        return String.valueOf(charge.getAmount());
    }

    public String getDescription() {
        return charge.getDescription();
    }

    public String getChargeId() {
        return String.valueOf(charge.getId());
    }

    public GatewayAccount getGatewayAccount() {
        return GatewayAccount.valueOf(charge.getGatewayAccount());
    }
}
