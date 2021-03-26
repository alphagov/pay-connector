package uk.gov.pay.connector.charge.model.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.common.model.api.ToLowerCaseStringSerializer;
import uk.gov.pay.connector.common.model.domain.Address;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class PersistedCard {

    @JsonProperty("last_digits_card_number")
    @JsonSerialize(using = ToStringSerializer.class)
    private LastDigitsCardNumber lastDigitsCardNumber;

    @JsonProperty("first_digits_card_number")
    @JsonSerialize(using = ToStringSerializer.class)
    private FirstDigitsCardNumber firstDigitsCardNumber;

    @JsonProperty("cardholder_name")
    private String cardHolderName;

    @JsonProperty("expiry_date")
    @JsonSerialize(using = ToStringSerializer.class)
    private CardExpiryDate expiryDate;

    @JsonProperty("billing_address")
    private Address billingAddress;

    @JsonProperty("card_brand")
    private String cardBrand;

    @JsonSerialize(using = ToLowerCaseStringSerializer.class)
    @JsonProperty("card_type")
    private CardType cardType;

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

    public CardType getCardType() {
        return cardType;
    }

    public PersistedCard setCardType(CardType cardType) {
        this.cardType = cardType;
        return this;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }

    public CardExpiryDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(CardExpiryDate expiryDate) {
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
}
