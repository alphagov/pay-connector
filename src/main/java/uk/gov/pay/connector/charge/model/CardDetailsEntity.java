package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import java.util.Optional;

@Embeddable
public class CardDetailsEntity {

    @Column(name = "first_digits_card_number")
    @JsonProperty("first_digits_card_number")
    @Convert(converter = FirstDigitsCardNumberConverter.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private FirstDigitsCardNumber firstDigitsCardNumber;

    @Column(name = "last_digits_card_number")
    @JsonProperty("last_digits_card_number")
    @Convert(converter = LastDigitsCardNumberConverter.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private LastDigitsCardNumber lastDigitsCardNumber;

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
    
    // For telephone payments
    public CardDetailsEntity(LastDigitsCardNumber lastDigitsCardNumber, FirstDigitsCardNumber firstDigitsCardNumber, String cardHolderName, String expiryDate, String cardBrand) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.firstDigitsCardNumber = firstDigitsCardNumber;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.cardBrand = cardBrand;
    }
    
    
    public CardDetailsEntity(FirstDigitsCardNumber firstDigitsCardNumber, LastDigitsCardNumber lastDigitsCardNumber, String cardHolderName, String expiryDate, String cardBrand, AddressEntity billingAddress) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.firstDigitsCardNumber = firstDigitsCardNumber;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.cardBrand = cardBrand;
        this.billingAddress = billingAddress;
    }

    public PersistedCard toCard() {
        PersistedCard card = new PersistedCard();
        card.setLastDigitsCardNumber(lastDigitsCardNumber);
        card.setFirstDigitsCardNumber(firstDigitsCardNumber);
        card.setCardBrand(cardBrand);
        card.setBillingAddress(billingAddress != null ? billingAddress.toAddress() : null);
        card.setExpiryDate(expiryDate);
        card.setCardHolderName(cardHolderName);
        return card;
    }

    public LastDigitsCardNumber getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public FirstDigitsCardNumber getFirstDigitsCardNumber() {
        return firstDigitsCardNumber;
    }

    public void setLastDigitsCardNumber(LastDigitsCardNumber lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
    }

    public void setFirstDigitsCardNumber(FirstDigitsCardNumber firstDigitsCardNumber) {
        this.firstDigitsCardNumber = firstDigitsCardNumber;
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

    public Optional<AddressEntity> getBillingAddress() {
        return Optional.ofNullable(billingAddress);
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
        if (firstDigitsCardNumber != null ? !firstDigitsCardNumber.equals(that.firstDigitsCardNumber) : that.firstDigitsCardNumber != null)
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
        result = 31 * result + (firstDigitsCardNumber != null ? firstDigitsCardNumber.hashCode() : 0);
        result = 31 * result + (cardHolderName != null ? cardHolderName.hashCode() : 0);
        result = 31 * result + (expiryDate != null ? expiryDate.hashCode() : 0);
        result = 31 * result + (cardBrand != null ? cardBrand.hashCode() : 0);
        result = 31 * result + (billingAddress != null ? billingAddress.hashCode() : 0);
        return result;
    }
}
