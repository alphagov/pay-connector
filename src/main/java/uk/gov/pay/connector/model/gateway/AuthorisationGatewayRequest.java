package uk.gov.pay.connector.model.gateway;

import uk.gov.pay.connector.model.GatewayRequest;
import uk.gov.pay.connector.model.domain.Card;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.util.Optional;

public class AuthorisationGatewayRequest implements GatewayRequest {
    private Card card;
    private ChargeEntity charge;

    public AuthorisationGatewayRequest(ChargeEntity charge, Card card) {
        this.charge = charge;
        this.card = card;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(charge.getGatewayTransactionId());
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

    @Override
    public GatewayAccountEntity getGatewayAccount() {return charge.getGatewayAccount();
    }

    public static AuthorisationGatewayRequest valueOf(ChargeEntity charge, Card card) {
        return new AuthorisationGatewayRequest(charge, card);
    }
}
