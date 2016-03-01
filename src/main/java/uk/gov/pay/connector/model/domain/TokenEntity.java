package uk.gov.pay.connector.model.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "tokens")
@SequenceGenerator(name="tokens_id_seq", sequenceName="tokens_id_seq", allocationSize=1)
public class TokenEntity extends AbstractEntity {

    @Column(name = "charge_id")
    private Long chargeId;

    @Column(name = "secure_redirect_token")
    private String token;

    public TokenEntity() {
    }

    public TokenEntity(Long chargeId, String token) {
        this.chargeId = chargeId;
        this.token = token;
    }

    public Long getChargeId() {
        return chargeId;
    }

    public String getToken() {
        return token;
    }
}
