package uk.gov.pay.connector.resources;

import java.util.Map;

public class CardDetailsValidator {

    public static boolean isValidCardDetails(Map<String, Object> cardDetails) {
        return cardDetails.containsKey("cvc") && is3Digits(cardDetails.get("cvc")) &&
                cardDetails.containsKey("card_number") && is16Digits(cardDetails.get("card_number")) &&
                cardDetails.containsKey("expiry_date") && hasExpiryDateFormat(cardDetails.get("expiry_date"));
    }

    private static boolean hasExpiryDateFormat(Object date) {
        return date != null && date.toString().matches("[0-9]{2}/[0-9]{2}");
    }

    private static boolean is16Digits(Object number) {
        return number != null && number.toString().matches("[0-9]{16}");
    }

    private static boolean is3Digits(Object number) {
        return number != null && number.toString().matches("[0-9]{3}");
    }
}
