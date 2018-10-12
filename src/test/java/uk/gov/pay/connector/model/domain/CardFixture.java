package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.gateway.model.AuthCardDetails;


public class CardFixture {
    private String cardNo = "1234567812345678";
    private String cardHolder = "Mr. Pay McPayment";
    private String cvc = "123";
    private String endDate = "02/18";
    private String cardBrand = "card-brand";

    private Address address = Address.aValidAddress();

    public static CardFixture aValidCard() {
        return new CardFixture();
    }

    public AuthCardDetails build() {
        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setAddress(address);
        authCardDetails.setCardHolder(cardHolder);
        authCardDetails.setCardNo(cardNo);
        authCardDetails.setCvc(cvc);
        authCardDetails.setEndDate(endDate);
        authCardDetails.setCardBrand(cardBrand);
        return authCardDetails;
    }

    public CardFixture withCardNo(String cardNo) {
        this.cardNo = cardNo;
        return this;
    }

    public CardFixture withCardHolder(String cardHolder) {
        this.cardHolder = cardHolder;
        return this;

    }

    public CardFixture withCvc(String cvc) {
        this.cvc = cvc;
        return this;

    }

    public CardFixture withEndDate(String endDate) {
        this.endDate = endDate;
        return this;

    }

    public CardFixture withAddress(Address address) {
        this.address = address;
        return this;
    }

    public CardFixture withCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
        return this;
    }
}
