package uk.gov.pay.connector.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

import static uk.gov.pay.connector.model.ChargeStatus.AUTHORIZATION_REJECTED;
import static uk.gov.pay.connector.model.ChargeStatus.SYSTEM_ERROR;

public class SandboxCardNumbers {

    public static boolean isValidCard(String cardNumber) {
        return GOOD_CARDS.contains(cardNumber);
    }

    public static boolean isInvalidCard(String cardNumber) {
        return ERROR_CARDS.containsKey(cardNumber);
    }

    public static CardError cardErrorFor(String cardNumber) {
        return ERROR_CARDS.get(cardNumber);
    }

    private static final String GOOD_CARD_NUMBER_1 = "4242424242424242";
    private static final String GOOD_CARD_NUMBER_2 = "5105105105105100";

    private static final List GOOD_CARDS = ImmutableList.of(GOOD_CARD_NUMBER_1, GOOD_CARD_NUMBER_2);

    private static final String DECLINED_CARD_NUMBER = "4000000000000002";
    private static final String CVC_ERROR_CARD_NUMBER = "4000000000000127";
    private static final String EXPIRED_CARD_NUMBER = "4000000000000069";
    private static final String PROCESSING_ERROR_CARD_NUMBER = "4000000000000119";

    private static final Map<String, CardError> ERROR_CARDS = ImmutableMap.of(
            DECLINED_CARD_NUMBER, new CardError(AUTHORIZATION_REJECTED, "This transaction was declined."),
            PROCESSING_ERROR_CARD_NUMBER, new CardError(SYSTEM_ERROR, "This transaction could be not be processed."),
            EXPIRED_CARD_NUMBER, new CardError(AUTHORIZATION_REJECTED, "The card is expired."),
            CVC_ERROR_CARD_NUMBER, new CardError(AUTHORIZATION_REJECTED, "The CVC code is incorrect."));
}