package uk.gov.pay.connector.model;

public class Card {

    private String cardNo;
    private String cardHolder;
    private String cvc;
    private String endDate;

    private Address address;

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

    public Card withAddress(Address address) {
        this.address = address;
        return this;
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
}
