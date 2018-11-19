package uk.gov.pay.connector.gateway.sandbox;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;

class SandboxCardNumbers {

    static boolean isValidCard(String cardNumber) {
        return GOOD_CARDS.contains(cardNumber) ||
                GOOD_CORPORATE_CARDS.contains(cardNumber);
    }

    static boolean isRejectedCard(String cardNumber) {
        return REJECTED_CARDS
                .keySet()
                .stream()
                .anyMatch(a -> a.contains(cardNumber));
    }

    static boolean isErrorCard(String cardNumber) {
        return ERROR_CARDS
                .keySet()
                .stream()
                .anyMatch(a -> a.contains(cardNumber));
    }

    static CardError cardErrorFor(String cardNumber) {
        return ERROR_CARDS
                .entrySet()
                .stream()
                .filter(a -> a.getKey().contains(cardNumber))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private static final List<String> GOOD_CARDS = ImmutableList.of(
            "4242",
            "4444333322221111",
            "4242424242424242",
            "4917610000000000003",
            "4000056655665556",
            "5105105105105100",
            "5200828282828210",
            "371449635398431",
            "3566002020360505",
            "6011000990139424",
            "36148900647913");

    private static final List<String> GOOD_CORPORATE_CARDS = ImmutableList.of(
            "4000180000000002",
            "5101180000000007");
    private static final List<String> DECLINED_CARD_NUMBERS = ImmutableList.of(
            "4000000000000002",
            "0002");
    private static final List<String> CVC_ERROR_CARD_NUMBERS = ImmutableList.of(
            "4000000000000127",
            "0127");
    private static final List<String> EXPIRED_CARD_NUMBERS = ImmutableList.of(
            "4000000000000069",
            "0069");
    private static final List<String> PROCESSING_ERROR_CARD_NUMBERS = ImmutableList.of(
            "4000000000000119",
            "0119");

    private static final Map<List<String>, CardError> ERROR_CARDS = ImmutableMap.of(
            PROCESSING_ERROR_CARD_NUMBERS, new CardError(AUTHORISATION_ERROR, "This transaction could be not be processed."));

    private static final Map<List<String>, CardError> REJECTED_CARDS = ImmutableMap.of(
            DECLINED_CARD_NUMBERS, new CardError(AUTHORISATION_REJECTED, "This transaction was declined."),
            EXPIRED_CARD_NUMBERS, new CardError(AUTHORISATION_REJECTED, "The card is expired."),
            CVC_ERROR_CARD_NUMBERS, new CardError(AUTHORISATION_REJECTED, "The CVC code is incorrect."));
    
}
