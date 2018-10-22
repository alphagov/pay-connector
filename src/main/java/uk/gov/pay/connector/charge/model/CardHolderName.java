package uk.gov.pay.connector.charge.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class CardHolderName {

    private final String cardHolderName;

    private CardHolderName(String cardHolderName) {
        this.cardHolderName = Objects.requireNonNull(cardHolderName);
    }

    public static CardHolderName of(String cardHolderName) {
        return new CardHolderName(cardHolderName);
    }

    public static CardHolderName ofNullable(String cardHolderName) {
        if (isBlank(cardHolderName)) {
            return null;
        }
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
