package uk.gov.pay.connector.resources;

import java.util.Map;

import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_CARD_NUMBER;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_CVC;
import static uk.gov.pay.connector.resources.CardDetailsResource.CardDetailsResourceKeys.FIELD_EXPIRY_DATE;

public class CardDetailsValidator {


    public static boolean isWellFormattedCardDetails(Map<String, Object> cardDetails) {
        return cardDetails.containsKey(FIELD_CARD_NUMBER) && isValidCardNumberLength(cardDetails.get(FIELD_CARD_NUMBER)) &&
                cardDetails.containsKey(FIELD_CVC) && is3Digits(cardDetails.get(FIELD_CVC)) &&
                cardDetails.containsKey(FIELD_EXPIRY_DATE) && hasExpiryDateFormat(cardDetails.get(FIELD_EXPIRY_DATE));
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
