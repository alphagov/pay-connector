package uk.gov.pay.connector.service.sandbox;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;

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

    private static final List GOOD_CARDS = ImmutableList.of(
            "4444333322221111",
            "4242424242424242",
            "5105105105105100",
            "348560871512574",
            "4485197542476643",
            "5582575229987470",
            "4917902691983168",
            "3528373272496082",
            "6011188510795021",
            "6763376639165982",
            "36375928148471");

    private static final String DECLINED_CARD_NUMBER = "4000000000000002";
    private static final String CVC_ERROR_CARD_NUMBER = "4000000000000127";
    private static final String EXPIRED_CARD_NUMBER = "4000000000000069";
    private static final String PROCESSING_ERROR_CARD_NUMBER = "4000000000000119";

    private static final Map<String, CardError> ERROR_CARDS = ImmutableMap.of(
            DECLINED_CARD_NUMBER, new CardError(AUTHORISATION_REJECTED, "This transaction was declined."),
            PROCESSING_ERROR_CARD_NUMBER, new CardError(AUTHORISATION_ERROR, "This transaction could be not be processed."),
            EXPIRED_CARD_NUMBER, new CardError(AUTHORISATION_REJECTED, "The card is expired."),
            CVC_ERROR_CARD_NUMBER, new CardError(AUTHORISATION_REJECTED, "The CVC code is incorrect."));
}