package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Card {

    private String cardNo;
    private String cardHolder;
    private String cvc;
    private String endDate;

    private AddressEntity address;

    private String cardBrand;

    public static Card aCard() {
        return new Card();
    }

    @JsonProperty("card_number")
    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    @JsonProperty("card_brand")
    public void setCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
    }

    @JsonProperty("cardholder_name")
    public void setCardHolder(String cardHolder) {
        this.cardHolder = cardHolder;
    }

    @JsonProperty("cvc")
    public void setCvc(String cvc) {
        this.cvc = cvc;
    }

    @JsonProperty("expiry_date")
    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    @JsonProperty("address")
    public void setAddress(AddressEntity address) {
        this.address = address;
    }

    public String getCardNo() {
        return cardNo;
    }

    public String getCardHolder() {
        return cardHolder;
    }

    public String getCvc() {
        return cvc;
    }

    public String getEndDate() {
        return endDate;
    }

    public AddressEntity getAddress() {
        return address;
    }

    public String getCardBrand() {
        return cardBrand;
    }
}
