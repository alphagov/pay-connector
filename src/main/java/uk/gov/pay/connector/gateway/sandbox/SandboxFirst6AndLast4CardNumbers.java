package uk.gov.pay.connector.gateway.sandbox;

import java.util.Map;
import java.util.Set;

public class SandboxFirst6AndLast4CardNumbers implements SandboxCardNumbers {

    @Override
    public boolean isValidCard(String cardNumber) {
        return setContains(cardNumber, GOOD_CARDS) || setContains(cardNumber, GOOD_CORPORATE_CARDS) || setContains(cardNumber, GOOD_CORPORATE_PREPAID_DEBIT_CARD);
    }

    @Override
    public boolean isRejectedCard(String cardNumber) {
        return setContains(cardNumber, REJECTED_CARDS);
    }

    @Override
    public boolean isErrorCard(String cardNumber) {
        return setContains(cardNumber, ERROR_CARDS.keySet());
    }
    
    private boolean setContains(String first6AndLast4CardNumber, Set<String> cardNumbers) {
        return cardNumbers.stream().anyMatch(fullCardNumber -> matchesFirst6AndLast4Digits(first6AndLast4CardNumber, fullCardNumber));
    }

    private boolean matchesFirst6AndLast4Digits(String first6AndLast4CardNumber, String fullCardNumber) {
        if (first6AndLast4CardNumber == null || first6AndLast4CardNumber.length() < 10) {
            return false;
        }
        return fullCardNumber.startsWith(first6AndLast4CardNumber.substring(0, 6)) && fullCardNumber.endsWith(first6AndLast4CardNumber.substring(first6AndLast4CardNumber.length() - 4));
    }

    @Override
    public CardError cardErrorFor(String first6AndLast4CardNumber) {
        return ERROR_CARDS.entrySet()
                .stream()
                .filter(errorCard -> matchesFirst6AndLast4Digits(first6AndLast4CardNumber, errorCard.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
