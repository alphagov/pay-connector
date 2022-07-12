package uk.gov.pay.connector.gateway.sandbox;

import java.util.Map;
import java.util.Set;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;

public class SandboxFullCardNumbers implements SandboxCardNumbers {
    
    private static final String GOOD_CARD_PREPAID_NON_CORPORATE = "4000160000000004";
    private static final String GOOD_CORPORATE_PREPAID_UNKNOWN_CREDIT_CARD = "5101180000000007";
    private static final String GOOD_CORPORATE_PREPAID_UNKNOWN_DEBIT_CARD = "5200828282828210";
    private static final String GOOD_NON_CORPORATE_NON_PREPAID = "4000020000000000";
    private static final String GOOD_MASTERCARD_CREDIT_CARD = "5101110000000004";
    private static final String GOOD_VISA_PREPAID_UNKNOWN_CREDIT_OR_DEBIT_UNKNOWN_CARD = "4000000000000010";
    private static final String DECLINED_CARD_NUMBER = "4000000000000002";
    private static final String CVC_ERROR_CARD_NUMBER = "4000000000000127";
    private static final String EXPIRED_CARD_NUMBER = "4000000000000069";
    private static final String PROCESSING_ERROR_CARD_NUMBER = "4000000000000119";

    private static final Set<String> GOOD_CARDS = Set.of(
            "4444333322221111",
            "4242424242424242",
            "4917610000000000003",
            "4000056655665556",
            "5105105105105100",
            "371449635398431",
            "3566002020360505",
            "6011000990139424",
            "36148900647913",
            GOOD_MASTERCARD_CREDIT_CARD,
            GOOD_VISA_PREPAID_UNKNOWN_CREDIT_OR_DEBIT_UNKNOWN_CARD,
            GOOD_CARD_PREPAID_NON_CORPORATE,
            GOOD_NON_CORPORATE_NON_PREPAID);

    private static final Set<String> GOOD_CORPORATE_CARDS = Set.of(
            "4988080000000000",
            "4000620000000007",
            "4293189100000008",
            GOOD_CORPORATE_PREPAID_UNKNOWN_CREDIT_CARD);

    private static final Set<String> GOOD_CORPORATE_PREPAID_DEBIT_CARD = Set.of(
            "4131840000000003",
            "4000180000000002",
            GOOD_CORPORATE_PREPAID_UNKNOWN_DEBIT_CARD);

    private static final Set<String> REJECTED_CARDS = Set.of(
            DECLINED_CARD_NUMBER,
            EXPIRED_CARD_NUMBER,
            CVC_ERROR_CARD_NUMBER);

    private static Map<String, CardError> ERROR_CARDS = Map.of(
            PROCESSING_ERROR_CARD_NUMBER, new CardError(AUTHORISATION_ERROR, "This transaction could be not be processed."));

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
