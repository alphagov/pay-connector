package uk.gov.pay.connector.gateway.sandbox;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;

public class SandboxCardNumbers {

    public static boolean isValidCard(String cardNumber) {
        return GOOD_CARDS.contains(cardNumber) ||
                GOOD_CORPORATE_CARDS.contains(cardNumber) ||
                GOOD_APPLE_PAY_LAST_DIGITS_CARD_NUMBER.equals(cardNumber) ||
                GOOD_CORPORATE_PREPAID_DEBIT_CARD.equals(cardNumber);
    }

    public static boolean isRejectedCard(String cardNumber) {
        return REJECTED_CARDS.contains(cardNumber);
    }

    public static boolean isErrorCard(String cardNumber) {
        return ERROR_CARDS.containsKey(cardNumber);
    }

    public static CardError cardErrorFor(String cardNumber) {
        return ERROR_CARDS.get(cardNumber);
    }

    private static final List<String> GOOD_CARDS = ImmutableList.of(
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
    private static final String GOOD_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "4242";
    private static final List<String> GOOD_CORPORATE_CARDS = ImmutableList.of(
            "4000180000000002",
            "5101180000000007");
    private static final String GOOD_CORPORATE_PREPAID_DEBIT_CARD = "4131840000000003";
    private static final String DECLINED_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "0002";
    private static final String DECLINED_CARD_NUMBER = "4000000000000002";
    private static final String CVC_ERROR_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "0127";
    private static final String CVC_ERROR_CARD_NUMBER = "4000000000000127";
    private static final String EXPIRED_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "0069";
    private static final String EXPIRED_CARD_NUMBER = "4000000000000069";
    private static final String PROCESSING_ERROR_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "0119";
    private static final String PROCESSING_ERROR_CARD_NUMBER = "4000000000000119";

    private static Map<String, CardError> ERROR_CARDS = Stream.of(
            PROCESSING_ERROR_CARD_NUMBER,
            PROCESSING_ERROR_APPLE_PAY_LAST_DIGITS_CARD_NUMBER)
            .collect(toMap(
                    Function.identity(),
                    cardNumber -> new CardError(AUTHORISATION_ERROR, "This transaction could be not be processed.")));

    private static final List<String>REJECTED_CARDS = ImmutableList.of(DECLINED_CARD_NUMBER, DECLINED_APPLE_PAY_LAST_DIGITS_CARD_NUMBER, EXPIRED_CARD_NUMBER, EXPIRED_APPLE_PAY_LAST_DIGITS_CARD_NUMBER, CVC_ERROR_CARD_NUMBER,
            CVC_ERROR_APPLE_PAY_LAST_DIGITS_CARD_NUMBER);
}
