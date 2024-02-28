package uk.gov.pay.connector.card.model.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.card.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.card.model.LastDigitsCardNumber;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.common.model.api.ToLowerCaseStringSerializer;
import uk.gov.pay.connector.common.model.domain.Address;
import uk.gov.service.payments.commons.model.CardExpiryDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class PersistedCard {

    @JsonProperty("last_digits_card_number")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(example = "4242", description = "The last 4 digits of the card the user paid with.")
    private LastDigitsCardNumber lastDigitsCardNumber;

    @JsonProperty("first_digits_card_number")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(example = "424242", description = "The first 6 digits of the card the user paid with.")
    private FirstDigitsCardNumber firstDigitsCardNumber;

    @JsonProperty("cardholder_name")
    @Schema(description = "The cardholder name the user entered when they paid.", example = "Joe B")
    private String cardHolderName;

    @JsonProperty("expiry_date")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "The expiry date of the card the user paid with.", example = "01/99")
    private CardExpiryDate expiryDate;

    @JsonProperty("billing_address")
    private Address billingAddress;

    @JsonProperty("card_brand")
    @Schema(example = "Visa")
    private String cardBrand;

    @JsonSerialize(using = ToLowerCaseStringSerializer.class)
    @JsonProperty("card_type")
    @Schema(example = "debit")
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
