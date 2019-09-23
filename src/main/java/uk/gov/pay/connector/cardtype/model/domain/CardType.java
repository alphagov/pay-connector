package uk.gov.pay.connector.cardtype.model.domain;

/**
 * Represents the absolute set of card type available.
 * Not to be confused with {@code PayersCardType}, which represents the result from card id where CREDIT_OR_DEBIT is
 * also an option.
 */
public enum CardType {
    CREDIT,
    DEBIT
}
