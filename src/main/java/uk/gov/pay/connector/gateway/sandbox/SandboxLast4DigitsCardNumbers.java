package uk.gov.pay.connector.gateway.sandbox;

import java.util.Map;
import java.util.Set;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;

public class SandboxLast4DigitsCardNumbers implements SandboxCardNumbers {
    
    /*
    The last four digits field for Apple and Google Pay aren't used for authorisation.
    However for testing purposes Sandbox determines the authorisation status based on the last four digits field.
     */

    private static final String GOOD_WALLET_LAST_DIGITS_CARD_NUMBER = "4242";
    private static final String GOOD_WALLET_EMPTY_STRING_CARD_NUMBER = "";
    private static final String DECLINED_WALLET_LAST_DIGITS_CARD_NUMBER = "0002";
    private static final String CVC_ERROR_WALLET_LAST_DIGITS_CARD_NUMBER = "0127";
    private static final String EXPIRED_WALLET_LAST_DIGITS_CARD_NUMBER = "0069";
    private static final String PROCESSING_ERROR_WALLET_LAST_DIGITS_CARD_NUMBER = "0119";

    private static final Set<String> GOOD_CARDS = Set.of(
            GOOD_WALLET_EMPTY_STRING_CARD_NUMBER,
            GOOD_WALLET_LAST_DIGITS_CARD_NUMBER);
    
    private static final Set<String> REJECTED_CARDS = Set.of(
            DECLINED_WALLET_LAST_DIGITS_CARD_NUMBER,
            EXPIRED_WALLET_LAST_DIGITS_CARD_NUMBER,
            CVC_ERROR_WALLET_LAST_DIGITS_CARD_NUMBER);

    private static Map<String, CardError> ERROR_CARDS = Map.of(
            PROCESSING_ERROR_WALLET_LAST_DIGITS_CARD_NUMBER, new CardError(AUTHORISATION_ERROR, "This transaction could be not be processed."));

    @Override
    public boolean isValidCard(String cardNumber) {
        return GOOD_CARDS.contains(cardNumber);
    }

    @Override
    public boolean isRejectedCard(String cardNumber) {
        return REJECTED_CARDS.contains(cardNumber);
    }

    @Override
    public boolean isErrorCard(String cardNumber) {
        return ERROR_CARDS.containsKey(cardNumber);
    }

    @Override
    public CardError cardErrorFor(String cardNumber) {
        return ERROR_CARDS.get(cardNumber);
    }

}
