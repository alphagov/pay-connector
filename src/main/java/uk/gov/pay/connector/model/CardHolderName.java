package uk.gov.pay.connector.model;

import java.util.Objects;

public class CardHolderName {

    private final String cardHolderName;

    private CardHolderName(String cardHolderName) {
        this.cardHolderName = Objects.requireNonNull(cardHolderName);
    }

    public static CardHolderName of(String cardHolderName) {
        return new CardHolderName(cardHolderName);
    }

    @Override
    public boolean equals(Object other) {
        if (other != null && other.getClass() == CardHolderName.class) {
            CardHolderName that = (CardHolderName) other;
            return this.cardHolderName.equals(that.cardHolderName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return cardHolderName.hashCode();
    }

    @Override
    public String toString() {
        return cardHolderName;
    }

}
