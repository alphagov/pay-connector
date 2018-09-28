package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthCardDetails implements AuthorisationDetails {

    private String cardNo;
    private String cardHolder;
    private String cvc;
    private String endDate;
    private Address address;
    private String cardBrand;
    private String userAgentHeader;
    private String acceptHeader;
    private PayersCardType payersCardType;
    private Boolean corporateCard;

    public static AuthCardDetails anAuthCardDetails() {
        return new AuthCardDetails();
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
    public void setAddress(Address address) {
        this.address = address;
    }

    @JsonProperty("user_agent_header")
    public void setUserAgentHeader(String userAgentHeader) {
        this.userAgentHeader = userAgentHeader;
    }

    @JsonProperty("accept_header")
    public void setAcceptHeader(String acceptHeader) {
        this.acceptHeader = acceptHeader;
    }

    @JsonProperty("corporate_card")
    public void setCorporateCard(Boolean corporateCard) {
        this.corporateCard = corporateCard;
    }

    @JsonProperty("card_type")
    public void setPayersCardType(PayersCardType payersCardType) {
        this.payersCardType = payersCardType;
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

    public Address getAddress() {
        return address;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getUserAgentHeader() {
        return userAgentHeader;
    }

    public String getAcceptHeader() {
        return acceptHeader;
    }

    public Boolean isCorporateCard() {
        return corporateCard;
    }

    public PayersCardType getPayersCardType() {
        return payersCardType;
    }
}
