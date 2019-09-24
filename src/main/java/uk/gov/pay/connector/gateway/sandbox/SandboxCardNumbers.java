package uk.gov.pay.connector.gateway.sandbox;

import com.google.common.collect.ImmutableSet;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;

public class SandboxCardNumbers {

    private static final String GOOD_CARD_PREPAID_NON_CORPORATE = "4000160000000004";
    private static final String GOOD_WALLET_LAST_DIGITS_CARD_NUMBER = "4242";
    private static final String GOOD_CORPORATE_PREPAID_UNKNOWN_CREDIT_CARD = "5101180000000007";
    private static final String GOOD_CORPORATE_PREPAID_UNKNOWN_DEBIT_CARD = "5200828282828210";
    private static final String GOOD_NON_CORPORATE_NON_PREPAID = "4000020000000000";
    private static final String GOOD_MASTERCARD_CREDIT_CARD = "5101110000000004";
    private static final String DECLINED_WALLET_LAST_DIGITS_CARD_NUMBER = "0002";
    private static final String DECLINED_CARD_NUMBER = "4000000000000002";
    private static final String CVC_ERROR_WALLET_LAST_DIGITS_CARD_NUMBER = "0127";
    private static final String CVC_ERROR_CARD_NUMBER = "4000000000000127";
    private static final String EXPIRED_WALLET_LAST_DIGITS_CARD_NUMBER = "0069";
    private static final String EXPIRED_CARD_NUMBER = "4000000000000069";
    private static final String PROCESSING_ERROR_WALLET_LAST_DIGITS_CARD_NUMBER = "0119";
    private static final String PROCESSING_ERROR_CARD_NUMBER = "4000000000000119";

    private static final Set<String> GOOD_CARDS = ImmutableSet.of(
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
            GOOD_WALLET_LAST_DIGITS_CARD_NUMBER,
            GOOD_CARD_PREPAID_NON_CORPORATE,
            GOOD_NON_CORPORATE_NON_PREPAID);

    private static final Set<String> GOOD_CORPORATE_CARDS = ImmutableSet.of(
            "4988080000000000",
            "4000620000000007",
            "4293189100000008",
            GOOD_CORPORATE_PREPAID_UNKNOWN_CREDIT_CARD);

    private static final Set<String> GOOD_CORPORATE_PREPAID_DEBIT_CARD = ImmutableSet.of(
            "4131840000000003",
            "4000180000000002",
            GOOD_CORPORATE_PREPAID_UNKNOWN_DEBIT_CARD);

    private static final Set<String> REJECTED_CARDS = ImmutableSet.of(
            DECLINED_CARD_NUMBER,
            DECLINED_WALLET_LAST_DIGITS_CARD_NUMBER,
            EXPIRED_CARD_NUMBER,
            EXPIRED_WALLET_LAST_DIGITS_CARD_NUMBER,
            CVC_ERROR_CARD_NUMBER,
            CVC_ERROR_WALLET_LAST_DIGITS_CARD_NUMBER);

    private static Map<String, CardError> ERROR_CARDS = Stream.of(
            PROCESSING_ERROR_CARD_NUMBER,
            PROCESSING_ERROR_WALLET_LAST_DIGITS_CARD_NUMBER)
            .collect(toMap(
                    Function.identity(),
                    cardNumber -> new CardError(AUTHORISATION_ERROR, "This transaction could be not be processed.")));

    public static boolean isValidCard(String cardNumber) {
        return GOOD_CARDS.contains(cardNumber) ||
                GOOD_CORPORATE_CARDS.contains(cardNumber) ||
                GOOD_CORPORATE_PREPAID_DEBIT_CARD.contains(cardNumber);
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
}
