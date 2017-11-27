package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;


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

    @Column(name = "worldpay_machine_cookie")
    private String worldpayMachineCookie;

    @Column(name = "charge_id")
    private Long chargeId;

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

    public String getWorldpayMachineCookie() {
        return worldpayMachineCookie;
    }

    public void setWorldpayMachineCookie(String worldpayMachineCookie) {
        this.worldpayMachineCookie = worldpayMachineCookie;
    }

    public Long getChargeId() {
        return chargeId;
    }

    public void setChargeId(Long chargeId) {
        this.chargeId = chargeId;
    }

    public static Card3dsEntity from(ChargeEntity chargeEntity) {

        Card3dsEntity entity = new Card3dsEntity();
        entity.setChargeId(chargeEntity.getId());
        entity.setIssuerUrl(chargeEntity.get3dsDetails().getIssuerUrl());
        entity.setPaRequest(chargeEntity.get3dsDetails().getPaRequest());
        entity.setWorldpayMachineCookie(chargeEntity.getProviderSessionId());

        return entity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Card3dsEntity that = (Card3dsEntity) o;

        if (!paRequest.equals(that.paRequest)) return false;
        if (!issuerUrl.equals(that.issuerUrl)) return false;
        if (worldpayMachineCookie != null ? !worldpayMachineCookie.equals(that.worldpayMachineCookie) : that.worldpayMachineCookie != null)
            return false;
        return chargeId.equals(that.chargeId);
    }

    @Override
    public int hashCode() {
        int result = paRequest.hashCode();
        result = 31 * result + issuerUrl.hashCode();
        result = 31 * result + (worldpayMachineCookie != null ? worldpayMachineCookie.hashCode() : 0);
        result = 31 * result + chargeId.hashCode();
        return result;
    }
}
