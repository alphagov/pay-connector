package uk.gov.pay.connector.resources;

import java.util.Map;

public class CardDetailsValidator {

    public static final String CARD_NUMBER_FIELD = "card_number";
    public static final String CVC_FIELD = "cvc";
    public static final String EXPIRY_DATE_FIELD = "expiry_date";

    public static boolean isWellFormattedCardDetails(Map<String, Object> cardDetails) {
        return cardDetails.containsKey(CARD_NUMBER_FIELD) && isValidCardNumberLength(cardDetails.get(CARD_NUMBER_FIELD)) &&
                cardDetails.containsKey(CVC_FIELD) && is3Digits(cardDetails.get(CVC_FIELD)) &&
                cardDetails.containsKey(EXPIRY_DATE_FIELD) && hasExpiryDateFormat(cardDetails.get(EXPIRY_DATE_FIELD));
    }

    private static boolean hasExpiryDateFormat(Object date) {
        return date != null && date.toString().matches("[0-9]{2}/[0-9]{2}");
    }

    private static boolean isValidCardNumberLength(Object number) {
        return number != null && number.toString().matches("[0-9]{14,16}");
    }

    private static boolean is3Digits(Object number) {
        return number != null && number.toString().matches("[0-9]{3}");
    }
}
