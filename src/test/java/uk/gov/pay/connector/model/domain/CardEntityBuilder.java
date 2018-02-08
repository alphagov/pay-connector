package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;

public final class CardEntityBuilder {
    private String lastDigitsCardNumber;
    private String cardHolderName;
    private String expiryDate;
    private String cardBrand;
    private AddressEntity billingAddress;
    private ChargeTransactionEntity chargeTransactionEntity;

    private CardEntityBuilder() {
    }

    public static CardEntityBuilder aCardEntity() {
        return new CardEntityBuilder();
    }

    public CardEntityBuilder withLastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        return this;
    }

    public CardEntityBuilder withCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
        return this;
    }

    public CardEntityBuilder withExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
        return this;
    }

    public CardEntityBuilder withCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
        return this;
    }

    public CardEntityBuilder withBillingAddress(AddressEntity billingAddress) {
        this.billingAddress = billingAddress;
        return this;
    }

    public CardEntityBuilder withChargeTransactionEntity(ChargeTransactionEntity chargeTransactionEntity) {
        this.chargeTransactionEntity = chargeTransactionEntity;
        return this;
    }

    public CardEntity build() {
        CardEntity cardEntity = new CardEntity();
        cardEntity.setLastDigitsCardNumber(lastDigitsCardNumber);
        cardEntity.setCardHolderName(cardHolderName);
        cardEntity.setExpiryDate(expiryDate);
        cardEntity.setCardBrand(cardBrand);
        cardEntity.setBillingAddress(billingAddress);
        cardEntity.setChargeTransactionEntity(chargeTransactionEntity);
        return cardEntity;
    }
}
