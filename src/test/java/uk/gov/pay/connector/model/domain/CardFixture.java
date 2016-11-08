package uk.gov.pay.connector.model.domain;

import static uk.gov.pay.connector.model.domain.AddressFixture.aValidAddress;

public class CardFixture {
    private String cardNo = "1234567812345678";
    private String cardHolder = "Mr. Pay McPayment";
    private String cvc = "123";
    private String endDate = "02/18";
    private String cardBrand = "card-brand";

    private AddressEntity addressEntity = aValidAddress().build();

    public static CardFixture aValidCard() {
        return new CardFixture();
    }

    public Card build() {
        Card card = new Card();
        card.setAddress(addressEntity);
        card.setCardHolder(cardHolder);
        card.setCardNo(cardNo);
        card.setCvc(cvc);
        card.setEndDate(endDate);
        card.setCardBrand(cardBrand);
        return card;
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

    public CardFixture withAddress(AddressEntity addressEntity) {
        this.addressEntity = addressEntity;
        return this;
    }

    public CardFixture withCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
        return this;
    }
}
