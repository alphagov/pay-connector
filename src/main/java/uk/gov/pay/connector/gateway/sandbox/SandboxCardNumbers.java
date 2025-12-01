package uk.gov.pay.connector.gateway.sandbox;

import java.util.Map;
import java.util.Set;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;

public interface SandboxCardNumbers {
    String GOOD_CARD_NUMBER = "4242424242424242";
    String GOOD_CARD_PREPAID_NON_CORPORATE = "4000160000000004";
    String GOOD_CORPORATE_PREPAID_UNKNOWN_CREDIT_CARD = "5101180000000007";
    String GOOD_CORPORATE_PREPAID_UNKNOWN_DEBIT_CARD = "5200828282828210";
    String GOOD_NON_CORPORATE_NON_PREPAID = "4000020000000000";
    String GOOD_MASTERCARD_CREDIT_CARD = "5555555555554444";
    String GOOD_VISA_PREPAID_UNKNOWN_CREDIT_OR_DEBIT_UNKNOWN_CARD = "4000000000000010";
    String DECLINED_CARD_NUMBER = "4000000000000002";
    String CVC_ERROR_CARD_NUMBER = "4000000000000127";
    String EXPIRED_CARD_NUMBER = "4000000000000069";
    String PROCESSING_ERROR_CARD_NUMBER = "4000000000000119";
    String RECURRING_FIRST_AUTHORISE_SUCCESS_SUBSEQUENT_DECLINE = "5105105105105100";

    Set<String> GOOD_CARDS = Set.of(
            "4444333322221111",
            "4917610000000000003",
            "4000056655665556",
            "371449635398431",
            "3566002020360505",
            "6011000990139424",
            "36148900647913",
            GOOD_CARD_NUMBER,
            RECURRING_FIRST_AUTHORISE_SUCCESS_SUBSEQUENT_DECLINE,
            GOOD_MASTERCARD_CREDIT_CARD,
            GOOD_VISA_PREPAID_UNKNOWN_CREDIT_OR_DEBIT_UNKNOWN_CARD,
            GOOD_CARD_PREPAID_NON_CORPORATE,
            GOOD_NON_CORPORATE_NON_PREPAID);

    Set<String> GOOD_CORPORATE_CARDS = Set.of(
            "4988080000000000",
            "4111111111111111",
            "4293189100000008",
            GOOD_CORPORATE_PREPAID_UNKNOWN_CREDIT_CARD);

    Set<String> GOOD_CORPORATE_PREPAID_DEBIT_CARD = Set.of(
            "4131840000000003",
            "4000180000000002",
            GOOD_CORPORATE_PREPAID_UNKNOWN_DEBIT_CARD);

    Set<String> REJECTED_CARDS = Set.of(
            DECLINED_CARD_NUMBER,
            EXPIRED_CARD_NUMBER,
            CVC_ERROR_CARD_NUMBER);

    Map<String, CardError> ERROR_CARDS = Map.of(
            PROCESSING_ERROR_CARD_NUMBER, new CardError(AUTHORISATION_ERROR, "This transaction could be not be processed."));

    boolean isValidCard(String cardNumber);

    boolean isRejectedCard(String cardNumber);

    boolean isErrorCard(String cardNumber);

    CardError cardErrorFor(String cardNumber);
}
