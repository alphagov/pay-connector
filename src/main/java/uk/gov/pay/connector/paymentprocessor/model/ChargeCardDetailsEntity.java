package uk.gov.pay.connector.paymentprocessor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.util.Optional;

@Entity
@Table(name = "charge_card_details")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "charge_card_details_id_seq",
        sequenceName = "charge_card_details_id_seq", allocationSize = 1)
public class ChargeCardDetailsEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "charge_card_details_id_seq")
    private Long id;

    @JoinColumn(name = "charge_id", updatable = false, insertable = false)
    @JsonIgnore
    private ChargeEntity chargeEntity;

    @Embedded
    private CardDetailsEntity cardDetails;

    @Embedded
    private Auth3dsRequiredEntity auth3dsRequiredDetails;

    @Column(name = "provider_session_id")
    private String providerSessionId;

    @Column(name = "exemption_3ds")
    @Enumerated(EnumType.STRING)
    private Exemption3ds exemption3ds;

    public ChargeCardDetailsEntity() {
    }
    
    public ChargeCardDetailsEntity(CardDetailsEntity cardDetails) {
        this.cardDetails = cardDetails;
    }

    public Optional<CardDetailsEntity> getCardDetails() {
        return Optional.ofNullable(cardDetails);
    }

    public void setCardDetails(CardDetailsEntity cardDetails) {
        this.cardDetails = cardDetails;
    }

    public Auth3dsRequiredEntity get3dsRequiredDetails() {
        return auth3dsRequiredDetails;
    }

    public void set3dsRequiredDetails(Auth3dsRequiredEntity auth3dsRequiredDetails) {
        this.auth3dsRequiredDetails = auth3dsRequiredDetails;
    }

    public String getProviderSessionId() {
        return providerSessionId;
    }

    public void setProviderSessionId(String providerSessionId) {
        this.providerSessionId = providerSessionId;
    }

    public Exemption3ds getExemption3ds() {
        return exemption3ds;
    }

    public void setExemption3ds(Exemption3ds exemption3ds) {
        this.exemption3ds = exemption3ds;
    }

    public LastDigitsCardNumber getLastDigitsCardNumber() {
        return getCardDetails().map(CardDetailsEntity::getLastDigitsCardNumber).orElse(null);
    }

    public FirstDigitsCardNumber getFirstDigitsCardNumber() {
        return getCardDetails().map(CardDetailsEntity::getFirstDigitsCardNumber).orElse(null);
    }

    public String getCardHolderName() {
        return getCardDetails().map(CardDetailsEntity::getCardHolderName).orElse(null);
    }
    

    public CardExpiryDate getExpiryDate() {
        return getCardDetails().map(CardDetailsEntity::getExpiryDate).orElse(null);
    }

    public Optional<AddressEntity> getBillingAddress() {
        return getCardDetails().map(CardDetailsEntity::getBillingAddress).orElse(null);
    }

    public String getCardBrand() {
        return getCardDetails().map(CardDetailsEntity::getCardBrand).orElse(null);
    }

    public CardType getCardType() {
        return getCardDetails().map(CardDetailsEntity::getCardType).orElse(null);
    }

    public Optional<CardBrandLabelEntity> getCardTypeDetails() {
        return getCardDetails().map(CardDetailsEntity::getCardTypeDetails).orElse(null);
    }
}
