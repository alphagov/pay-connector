package uk.gov.pay.connector.model.domain;

import static uk.gov.pay.connector.model.domain.AddressEntityFixture.aValidAddressEntity;

public final class CardDetailsEntityFixture {
    private String lastDigitsCardNumber = "0123";
    private String cardHolderName = "Mr Payment Name";
    private String expiryDate = "03/20";
    private String cardBrand = "visa";
    private AddressEntity billingAddress = aValidAddressEntity().build();

    private CardDetailsEntityFixture() {
    }

    public static CardDetailsEntityFixture aValidCardDetailsEntity() {
        return new CardDetailsEntityFixture();
    }

    public CardDetailsEntityFixture withLastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        return this;
    }

    public CardDetailsEntityFixture withCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
        return this;
    }

    public CardDetailsEntityFixture withExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
        return this;
    }

    public CardDetailsEntityFixture withCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
        return this;
    }

    public CardDetailsEntityFixture withBillingAddress(AddressEntity billingAddress) {
        this.billingAddress = billingAddress;
        return this;
    }

    public CardDetailsEntity build() {
        CardDetailsEntity cardDetailsEntity = new CardDetailsEntity();
        cardDetailsEntity.setLastDigitsCardNumber(lastDigitsCardNumber);
        cardDetailsEntity.setCardHolderName(cardHolderName);
        cardDetailsEntity.setExpiryDate(expiryDate);
        cardDetailsEntity.setCardBrand(cardBrand);
        cardDetailsEntity.setBillingAddress(billingAddress);
        return cardDetailsEntity;
    }
}
