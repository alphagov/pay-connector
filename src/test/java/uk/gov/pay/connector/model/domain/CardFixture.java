package uk.gov.pay.connector.model.domain;

import static uk.gov.pay.connector.model.domain.AddressFixture.aValidAddress;

public class CardFixture {
    private String cardNo = "1234567812345678";
    private String cardHolder = "Mr. Pay McPayment";
    private String cvc = "123";
    private String endDate = "02/18";
    private String cardBrand = "card-brand";

    private Address address = aValidAddress().build();

    public static CardFixture aValidCard() {
        return new CardFixture();
    }

    public AuthorisationDetails build() {
        AuthorisationDetails authorisationDetails = new AuthorisationDetails();
        authorisationDetails.setAddress(address);
        authorisationDetails.setCardHolder(cardHolder);
        authorisationDetails.setCardNo(cardNo);
        authorisationDetails.setCvc(cvc);
        authorisationDetails.setEndDate(endDate);
        authorisationDetails.setCardBrand(cardBrand);
        return authorisationDetails;
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
