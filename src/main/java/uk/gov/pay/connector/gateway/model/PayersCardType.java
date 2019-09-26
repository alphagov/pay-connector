package uk.gov.pay.connector.gateway.model;

import uk.gov.pay.connector.cardtype.model.domain.CardType;

/**
 * The enum type that should be used to map from and to JSON when
 * dealing with what type (CREDIT, DEBIT, CREDIT_OR_DEBIT) card is
 * used to make a payment. This is also used to calculate corporate
 * surcharges, based on other rules.
 */
public enum PayersCardType {
    DEBIT,
    CREDIT,
    CREDIT_OR_DEBIT;
    
    public static CardType toCardType(PayersCardType payersCardType) {
        switch (payersCardType) {
            case DEBIT:
                return CardType.DEBIT;
            case CREDIT:
                return CardType.CREDIT;
            default: 
                return null;
        }
    }
}
