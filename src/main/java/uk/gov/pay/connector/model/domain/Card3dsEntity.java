package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.Objects;


@Entity
@Table(name = "card_3ds")
@SequenceGenerator(name = "card_3ds_id_seq",
        sequenceName = "card_3ds_id_seq", allocationSize = 1)
public class Card3dsEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "card_3ds_id_seq")
    @JsonIgnore
    private Long id;

    @Column(name = "pa_request")
    private String paRequest;

    @Column(name = "issuer_url")
    private String issuerUrl;

    //TODO: rename this column to provider_session_id if we decide to continue with this table in the longer run.
     // For now this column is Worldpay specific. And this table is currently kept only for backward compatibility with the transaction table changes.
    @Column(name = "worldpay_machine_cookie")
    private String providerSessionId;

    @Column(name="html_out")
    private String htmlOut;

    @Column(name="md")
    private String md;

    @OneToOne
    @JoinColumn(name = "transaction_id", referencedColumnName = "id", updatable = true)
    private ChargeTransactionEntity chargeTransactionEntity;

    public Card3dsEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPaRequest() {
        return paRequest;
    }

    public void setPaRequest(String paRequest) {
        this.paRequest = paRequest;
    }

    public String getIssuerUrl() {
        return issuerUrl;
    }

    public void setIssuerUrl(String issuerUrl) {
        this.issuerUrl = issuerUrl;
    }

    public String getProviderSessionId() {
        return providerSessionId;
    }

    public void setProviderSessionId(String providerSessionId) {
        this.providerSessionId = providerSessionId;
    }

    public ChargeTransactionEntity getChargeTransactionEntity() {
        return chargeTransactionEntity;
    }

    public void setChargeTransactionEntity(ChargeTransactionEntity chargeTransactionEntity) {
        this.chargeTransactionEntity = chargeTransactionEntity;
    }

    public String getHtmlOut() {
        return htmlOut;
    }

    public void setHtmlOut(String htmlOut) {
        this.htmlOut = htmlOut;
    }

    public String getMd() {
        return md;
    }

    public void setMd(String md) {
        this.md = md;
    }

    public static Card3dsEntity from(ChargeEntity chargeEntity) {
        Card3dsEntity entity = new Card3dsEntity();
        entity.setIssuerUrl(chargeEntity.get3dsDetails().getIssuerUrl());
        entity.setPaRequest(chargeEntity.get3dsDetails().getPaRequest());
        entity.setProviderSessionId(chargeEntity.getProviderSessionId());
        entity.setHtmlOut(chargeEntity.get3dsDetails().getHtmlOut());
        entity.setMd(chargeEntity.get3dsDetails().getMd());
        return entity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card3dsEntity that = (Card3dsEntity) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(paRequest, that.paRequest) &&
                Objects.equals(issuerUrl, that.issuerUrl) &&
                Objects.equals(providerSessionId, that.providerSessionId) &&
                Objects.equals(htmlOut, that.htmlOut) &&
                Objects.equals(md, that.md) &&
                Objects.equals(chargeTransactionEntity, that.chargeTransactionEntity);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id, paRequest, issuerUrl, providerSessionId, htmlOut, md, chargeTransactionEntity);
    }
}
