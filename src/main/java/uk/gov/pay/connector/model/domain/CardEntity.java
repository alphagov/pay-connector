package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "cards")
@SequenceGenerator(name = "cards_id_seq",
        sequenceName = "cards_id_seq", allocationSize = 1)
public class CardEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cards_id_seq")
    @JsonIgnore
    private Long id;

    @Column(name = "last_digits_card_number")
    private String lastDigitsCardNumber;

    @Column(name = "cardholder_name")
    private String cardHolderName;

    @Column(name = "expiry_date")
    private String expiryDate;

    @Column(name = "card_brand")
    private String cardBrand;

    @Embedded
    private AddressEntity billingAddress;

    @JoinColumn(name = "transaction_id", referencedColumnName = "id", updatable = false)
    private ChargeTransactionEntity chargeTransactionEntity;

    public CardEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public void setLastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(String cardHolder) {
        this.cardHolderName = cardHolder;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public AddressEntity getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(AddressEntity billingAddress) {
        this.billingAddress = billingAddress;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    public ChargeTransactionEntity getChargeTransactionEntity() {
        return chargeTransactionEntity;
    }

    public void setChargeTransactionEntity(ChargeTransactionEntity chargeTransactionEntity) {
        this.chargeTransactionEntity = chargeTransactionEntity;
    }

    public static CardEntity from(CardDetailsEntity cardDetailsEntity, ChargeTransactionEntity chargeTransactionEntity) {
        CardEntity entity = new CardEntity();
        entity.setBillingAddress(cardDetailsEntity.getBillingAddress());
        entity.setCardBrand(cardDetailsEntity.getCardBrand());
        entity.setCardHolderName(cardDetailsEntity.getCardHolderName());
        entity.setExpiryDate(cardDetailsEntity.getExpiryDate());
        entity.setLastDigitsCardNumber(cardDetailsEntity.getLastDigitsCardNumber());
        entity.setChargeTransactionEntity(chargeTransactionEntity);

        return entity;
    }

}
