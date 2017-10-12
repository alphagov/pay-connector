package uk.gov.pay.connector.model.domain;

import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Table;

@Entity
@Table(name = "cards")
public class CardEntity extends AbstractEntity {
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

    @Column(name = "email")
    private String email;

    @Column(name = "charge_id")
    private Long chargeId;

    public CardEntity() {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CardEntity that = (CardEntity) o;

        if (lastDigitsCardNumber != null ? !lastDigitsCardNumber.equals(that.lastDigitsCardNumber) : that.lastDigitsCardNumber != null)
            return false;
        if (cardHolderName != null ? !cardHolderName.equals(that.cardHolderName) : that.cardHolderName != null)
            return false;
        if (expiryDate != null ? !expiryDate.equals(that.expiryDate) : that.expiryDate != null)
            return false;
        if (cardBrand != null ? !cardBrand.equals(that.cardBrand) : that.cardBrand != null)
            return false;
        if (email != null ? !email.equals(that.email) : that.email != null) return false;
        if (chargeId != null ? !chargeId.equals(that.chargeId) : that.chargeId != null)
            return false;
        return billingAddress != null ? billingAddress.equals(that.billingAddress) : that.billingAddress == null;
    }

    @Override
    public int hashCode() {
        int result = lastDigitsCardNumber != null ? lastDigitsCardNumber.hashCode() : 0;
        result = 31 * result + (cardHolderName != null ? cardHolderName.hashCode() : 0);
        result = 31 * result + (expiryDate != null ? expiryDate.hashCode() : 0);
        result = 31 * result + (cardBrand != null ? cardBrand.hashCode() : 0);
        result = 31 * result + (billingAddress != null ? billingAddress.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (chargeId != null ? chargeId.hashCode() : 0);
        return result;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public static CardEntity from(CardDetailsEntity cardDetailsEntity, String email, Long chargeId) {
        CardEntity entity = new CardEntity();
        entity.setBillingAddress(cardDetailsEntity.getBillingAddress());
        entity.setCardBrand(cardDetailsEntity.getCardBrand());
        entity.setCardHolderName(cardDetailsEntity.getCardHolderName());
        entity.setExpiryDate(cardDetailsEntity.getExpiryDate());
        entity.setLastDigitsCardNumber(cardDetailsEntity.getLastDigitsCardNumber());
        entity.setEmail(email);
        entity.setChargeId(chargeId);

        return entity;
    }

    public Long getChargeId() {
        return chargeId;
    }

    public void setChargeId(Long chargeId) {
        this.chargeId = chargeId;
    }
}
