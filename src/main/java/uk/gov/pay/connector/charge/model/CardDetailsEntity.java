package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;
import uk.gov.pay.connector.common.model.api.ToLowerCaseStringSerializer;
import uk.gov.service.payments.commons.jpa.CardExpiryDateConverter;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.util.Objects;
import java.util.Optional;

@Embeddable
public class CardDetailsEntity {

    @Column(name = "first_digits_card_number")
    @JsonProperty("first_digits_card_number")
    @Schema(example = "424242")
    @Convert(converter = FirstDigitsCardNumberConverter.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private FirstDigitsCardNumber firstDigitsCardNumber;

    @Column(name = "last_digits_card_number")
    @JsonProperty("last_digits_card_number")
    @Schema(example = "4242")
    @Convert(converter = LastDigitsCardNumberConverter.class)
    @JsonSerialize(using = ToStringSerializer.class)
    private LastDigitsCardNumber lastDigitsCardNumber;

    @Column(name = "cardholder_name")
    @JsonProperty("cardholder_name")
    @Schema(example = "Joe B")
    private String cardHolderName;

    @Column(name = "expiry_date")
    @Convert(converter = CardExpiryDateConverter.class)
    @JsonProperty("expiry_date")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(example = "01/99")
    private CardExpiryDate expiryDate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_brand", referencedColumnName = "brand", updatable = false, insertable = false)
    private CardBrandLabelEntity cardTypeDetails;

    @Column(name = "card_brand")
    @Schema(example = "visa")
    private String cardBrand;

    @Column(name = "card_type")
    @Enumerated(EnumType.STRING)
    @JsonProperty("card_type")
    @JsonSerialize(using = ToLowerCaseStringSerializer.class)
    @Schema(example = "credit")
    private CardType cardType;

    @Embedded
    @JsonProperty("billing_address")
    private AddressEntity billingAddress;

    public CardDetailsEntity() {
    }

    public CardDetailsEntity(LastDigitsCardNumber lastDigitsCardNumber, FirstDigitsCardNumber firstDigitsCardNumber, String cardHolderName,
                             CardExpiryDate expiryDate, String cardBrand, CardType cardType) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.firstDigitsCardNumber = firstDigitsCardNumber;
        this.cardHolderName = cardHolderName;
        this.expiryDate = expiryDate;
        this.cardBrand = cardBrand;
        this.cardType = cardType;
    }

    public CardDetailsEntity(FirstDigitsCardNumber firstDigitsCardNumber, LastDigitsCardNumber lastDigitsCardNumber, String cardHolderName,
                             CardExpiryDate expiryDate, String cardBrand, CardType cardType, AddressEntity billingAddress) {
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

    public CardExpiryDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(CardExpiryDate expiryDate) {
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

    public CardType getCardType() {
        return cardType;
    }

    public CardDetailsEntity setCardType(CardType cardType) {
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

    public Optional<CardBrandLabelEntity> getCardTypeDetails() {
        return Optional.ofNullable(cardTypeDetails);
    }

    public void setCardTypeDetails(CardBrandLabelEntity cardTypeDetails) {
        this.cardTypeDetails = cardTypeDetails;
    }
}
