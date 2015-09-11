package uk.gov.pay.connector.model;

public class Card {

    private String cardNo;
    private String cardHolder;
    private String cvc;
    private String endDate;
    private String addressLine1;
    private String addressLine2;
    private String addressZip;
    private String addressCity;
    private String addressState;
    private static final String DEFAULT_COUNTRY = "GB";

    public static Card aCard() {
        return new Card();
    }

    public Card withCardDetails(String cardHolder, String cardNo, String cvc, String endDate) {
        this.cardHolder = cardHolder;
        this.cardNo = cardNo;
        this.cvc = cvc;
        this.endDate = endDate;

        return this;
    }

    public Card withAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
        return this;
    }

    public Card withAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
        return this;
    }

    public Card withAddressZip(String addressZip) {
        this.addressZip = addressZip;
        return this;
    }

    public Card withAddressCity(String addressCity) {
        this.addressCity = addressCity;
        return this;
    }

    public Card withAddressState(String addressState) {
        this.addressState = addressState;
        return this;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressCountry() {
        return DEFAULT_COUNTRY;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getAddressZip() {
        return addressZip;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public String getAddressState() {
        return addressState;
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
}
