package uk.gov.pay.connector.gateway.sandbox;

public class SandboxFullCardNumbers implements SandboxCardNumbers {

    @Override
    public boolean isValidCard(String cardNumber) {
        return GOOD_CARDS.contains(cardNumber) ||
                GOOD_CORPORATE_CARDS.contains(cardNumber) ||
                GOOD_CORPORATE_PREPAID_DEBIT_CARD.contains(cardNumber);
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
