package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class PersistedCard {

    @JsonProperty("last_digits_card_number")
    private String lastDigitsCardNumber;

    @JsonProperty("cardholder_name")
    private String cardHolderName;

    @JsonProperty("expiry_date")
    private String expiryDate;

    @JsonProperty("billing_address")
    private Address billingAddress;

    @JsonProperty("card_brand")
    private String cardBrand;

    public String getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public void setLastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Address getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(Address billingAddress) {
        this.billingAddress = billingAddress;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    public static PersistedCard from(CardEntity cardEntity, String cardBrand) {
            PersistedCard card = new PersistedCard();
            card.setLastDigitsCardNumber(cardEntity.getLastDigitsCardNumber());
            card.setCardBrand(cardEntity.getCardBrand());
            card.setBillingAddress(cardEntity.getBillingAddress() != null ? cardEntity.getBillingAddress().toAddress() : null);
            card.setExpiryDate(cardEntity.getExpiryDate());
            card.setCardHolderName(cardEntity.getCardHolderName());
            card.setCardBrand(cardBrand);

            return card;
    }
}
