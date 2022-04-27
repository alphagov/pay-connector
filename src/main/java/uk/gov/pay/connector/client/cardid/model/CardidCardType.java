package uk.gov.pay.connector.client.cardid.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.cardtype.model.domain.CardType;
import uk.gov.pay.connector.gateway.model.PayersCardType;

public enum CardidCardType {
    @JsonProperty("D")
    DEBIT,
    @JsonProperty("C")
    CREDIT,
    @JsonProperty("CD")
    CREDIT_OR_DEBIT;

    public static CardType toCardType(CardidCardType cardidCardType) {
        switch (cardidCardType) {
            case DEBIT:
                return CardType.DEBIT;
            case CREDIT:
                return CardType.CREDIT;
            default:
                return null;
        }
    }

    public static PayersCardType toPayersCardType(CardidCardType cardidCardType) {
        switch (cardidCardType) {
            case DEBIT:
                return PayersCardType.DEBIT;
            case CREDIT:
                return PayersCardType.CREDIT;
            case CREDIT_OR_DEBIT:
                return PayersCardType.CREDIT_OR_DEBIT;
            default:
                return null;
        }
    }
}
