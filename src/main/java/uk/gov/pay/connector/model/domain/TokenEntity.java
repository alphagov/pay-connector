package uk.gov.pay.connector.model.domain;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tokens")
@SequenceGenerator(name = "tokens_id_seq", sequenceName = "tokens_id_seq", allocationSize = 1)
public class TokenEntity extends AbstractEntity {

    @Column(name = "secure_redirect_token")
    private String token;

    @ManyToOne
    @JoinColumn(name = "charge_id", nullable = false)
    ChargeEntity chargeEntity;

    public TokenEntity() {
    }

    public static TokenEntity generateNewTokenFor(ChargeEntity chargeEntity) {
        TokenEntity tokenEntity = new TokenEntity();
        tokenEntity.setChargeEntity(chargeEntity);
        tokenEntity.setToken(UUID.randomUUID().toString());
        return tokenEntity;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public ChargeEntity getChargeEntity() {
        return chargeEntity;
    }

    public void setChargeEntity(ChargeEntity chargeEntity) {
        this.chargeEntity = chargeEntity;
    }
}
