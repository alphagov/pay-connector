package uk.gov.pay.connector.cardtype.dao;

import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.cardtype.model.domain.CardTypeEntity;

import java.util.UUID;

public final class CardTypeEntityBuilder {
    private UUID id = UUID.randomUUID();
    private String brand = "Visa";
    private String label = "visa";
    private CardType type = CardType.DEBIT;
    private boolean requires3ds = false;

    private CardTypeEntityBuilder() {
    }

    public static CardTypeEntityBuilder aCardTypeEntity() {
        return new CardTypeEntityBuilder();
    }

    public CardTypeEntityBuilder withBrand(String brand) {
        this.brand = brand;
        return this;
    }

    public CardTypeEntityBuilder withLabel(String label) {
        this.label = label;
        return this;
    }

    public CardTypeEntityBuilder withType(CardType type) {
        this.type = type;
        return this;
    }

    public CardTypeEntityBuilder withRequires3ds(boolean requires3ds) {
        this.requires3ds = requires3ds;
        return this;
    }

    public CardTypeEntity build() {
        CardTypeEntity cardTypeEntity = new CardTypeEntity();
        cardTypeEntity.setId(id);
        cardTypeEntity.setBrand(brand);
        cardTypeEntity.setLabel(label);
        cardTypeEntity.setType(type);
        cardTypeEntity.setRequires3ds(requires3ds);
        return cardTypeEntity;
    }
}
