package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import uk.gov.pay.connector.cardtype.model.domain.SupportedType;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import java.util.Objects;
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
    
    @Convert(converter = SupportedTypeConverter.class)
    @JsonSerialize(using = ToStringSerializer.class)
    @Column(name = "card_type")
    private SupportedType cardType;

    @Embedded
    @JsonProperty("billing_address")
    private AddressEntity billingAddress;

    public CardDetailsEntity() {
    }
    
    public CardDetailsEntity(LastDigitsCardNumber lastDigitsCardNumber, FirstDigitsCardNumber firstDigitsCardNumber, String cardHolderName, String expiryDate, String cardBrand, SupportedType cardType) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.firstDigitsCardNumber = firstDigitsCardNumber;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.cardBrand = cardBrand;
        this.cardType = cardType;
    }

    public CardDetailsEntity(FirstDigitsCardNumber firstDigitsCardNumber, LastDigitsCardNumber lastDigitsCardNumber, String cardHolderName, String expiryDate, String cardBrand, SupportedType cardType, AddressEntity billingAddress) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.firstDigitsCardNumber = firstDigitsCardNumber;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.cardBrand = cardBrand;
        this.cardType = cardType;
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
        card.setCardType(cardType);
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

    public SupportedType getCardType() {
        return cardType;
    }

    public CardDetailsEntity setCardType(SupportedType cardType) {
        this.cardType = cardType;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardDetailsEntity that = (CardDetailsEntity) o;
        return Objects.equals(firstDigitsCardNumber, that.firstDigitsCardNumber) &&
                Objects.equals(lastDigitsCardNumber, that.lastDigitsCardNumber) &&
                Objects.equals(cardHolderName, that.cardHolderName) &&
                Objects.equals(expiryDate, that.expiryDate) &&
                Objects.equals(cardBrand, that.cardBrand) &&
                cardType == that.cardType &&
                Objects.equals(billingAddress, that.billingAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstDigitsCardNumber, lastDigitsCardNumber, cardHolderName, expiryDate, cardBrand, cardType, billingAddress);
    }
}
