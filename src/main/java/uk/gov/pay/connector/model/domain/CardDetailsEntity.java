package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;

@Embeddable
public class CardDetailsEntity {

    @Column(name = "last_digits_card_number")
    @JsonProperty("last_digits_card_number")
    private String lastDigitsCardNumber;

    @Column(name = "cardholder_name")
    @JsonProperty("cardholder_name")
    private String cardHolderName;

    @Column(name = "expiry_date")
    @JsonProperty("expiry_date")
    private String expiryDate;

    @Column(name = "card_brand")
    private String cardBrand;

    @Embedded
    @JsonProperty("billing_address")
    private AddressEntity billingAddress;

    public CardDetailsEntity() {
    }

    public PersistedCard toCard() {
        PersistedCard card = new PersistedCard();
        card.setLastDigitsCardNumber(lastDigitsCardNumber);
        card.setCardBrand(cardBrand);
        card.setBillingAddress(billingAddress != null ? billingAddress.toAddress() : null);
        card.setExpiryDate(expiryDate);
        card.setCardHolderName(cardHolderName);
        return card;
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

        CardDetailsEntity that = (CardDetailsEntity) o;

        if (lastDigitsCardNumber != null ? !lastDigitsCardNumber.equals(that.lastDigitsCardNumber) : that.lastDigitsCardNumber != null)
            return false;
        if (cardHolderName != null ? !cardHolderName.equals(that.cardHolderName) : that.cardHolderName != null)
            return false;
        if (expiryDate != null ? !expiryDate.equals(that.expiryDate) : that.expiryDate != null) return false;
        if (cardBrand != null ? !cardBrand.equals(that.cardBrand) : that.cardBrand != null) return false;
        return billingAddress != null ? billingAddress.equals(that.billingAddress) : that.billingAddress == null;

    }

    @Override
    public int hashCode() {
        int result = lastDigitsCardNumber != null ? lastDigitsCardNumber.hashCode() : 0;
        result = 31 * result + (cardHolderName != null ? cardHolderName.hashCode() : 0);
        result = 31 * result + (expiryDate != null ? expiryDate.hashCode() : 0);
        result = 31 * result + (cardBrand != null ? cardBrand.hashCode() : 0);
        result = 31 * result + (billingAddress != null ? billingAddress.hashCode() : 0);
        return result;
    }
}
