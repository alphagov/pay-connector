package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tokens")
@SequenceGenerator(name = "tokens_id_seq",
        sequenceName = "tokens_id_seq", allocationSize = 1)
public class TokenEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tokens_id_seq")
    @JsonIgnore
    private Long id;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
