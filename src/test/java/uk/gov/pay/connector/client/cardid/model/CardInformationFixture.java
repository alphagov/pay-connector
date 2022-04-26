package uk.gov.pay.connector.client.cardid.model;

import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;

public final class CardInformationFixture {
    private String brand = "visa";
    private CardidCardType type = CardidCardType.CREDIT;
    private String label = "VISA DEBIT";
    private boolean corporate;
    private PayersCardPrepaidStatus prepaidStatus = PayersCardPrepaidStatus.NOT_PREPAID;

    private CardInformationFixture() {
    }

    public static CardInformationFixture aCardInformation() {
        return new CardInformationFixture();
    }

    public CardInformationFixture withBrand(String brand) {
        this.brand = brand;
        return this;
    }

    public CardInformationFixture withType(CardidCardType type) {
        this.type = type;
        return this;
    }

    public CardInformationFixture withLabel(String label) {
        this.label = label;
        return this;
    }

    public CardInformationFixture withCorporate(boolean corporate) {
        this.corporate = corporate;
        return this;
    }

    public CardInformationFixture withPrepaidStatus(PayersCardPrepaidStatus prepaidStatus) {
        this.prepaidStatus = prepaidStatus;
        return this;
    }

    public CardInformation build() {
        return new CardInformation(brand, type, label, corporate, prepaidStatus);
    }
}
