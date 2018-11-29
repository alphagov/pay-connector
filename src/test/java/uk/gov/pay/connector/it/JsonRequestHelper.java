package uk.gov.pay.connector.it;

import com.google.gson.JsonObject;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gateway.model.PayersCardType;

import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class JsonRequestHelper {

    private static final String CVC = "123";
    private static final String EXPIRY_DATE = "11/99";
    private static final String CARD_HOLDER_NAME = "Scrooge McDuck";
    private static final String ADDRESS_LINE_1 = "The Money Pool";
    private static final String ADDRESS_CITY = "London";
    private static final String ADDRESS_POSTCODE = "DO11 4RS";
    private static final String ADDRESS_COUNTRY_GB = "GB";
    private static final String CARD_NUMBER = "4242424242424242";
    private static final String CARD_BRAND = "cardBrand";
    private static final String ADDRESS_LINE_2 = "Moneybags Avenue";
    private static final String ADDRESS_COUNTY = "Greater London";
    private static final PayersCardPrepaidStatus PREPAID_STATUS = PayersCardPrepaidStatus.NOT_PREPAID;

    public static String buildJsonAuthorisationDetailsFor(String cardNumber, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(cardNumber, CVC, EXPIRY_DATE, cardBrand);
    }

    public static String buildJsonAuthorisationDetailsFor(String cardHolderName, String cardNumber, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(cardHolderName, cardNumber, CVC, EXPIRY_DATE, cardBrand, ADDRESS_LINE_1,
                null, ADDRESS_CITY, null, ADDRESS_POSTCODE, ADDRESS_COUNTRY_GB);
    }

    public static String buildJsonAuthorisationDetailsFor(String cardNumber, String cvc, String expiryDate, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(CARD_HOLDER_NAME, cardNumber, cvc, expiryDate, cardBrand, ADDRESS_LINE_1,
                null, ADDRESS_CITY, null, ADDRESS_POSTCODE, ADDRESS_COUNTRY_GB);
    }

    public static String buildDetailedJsonAuthorisationDetailsFor(String cardNumber,
                                                                  String cvc,
                                                                  String expiryDate,
                                                                  String cardBrand,
                                                                  String cardHolderName,
                                                                  String addressLine1,
                                                                  String addressLine2,
                                                                  String city,
                                                                  String county,
                                                                  String postcode,
                                                                  String country) {
        return buildJsonAuthorisationDetailsFor(cardHolderName, cardNumber, cvc, expiryDate, cardBrand, addressLine1, addressLine2, city, county, postcode, country);
    }

    public static String buildJsonAuthorisationDetailsFor(String cardHolderName,
                                                          String cardNumber,
                                                          String cvc,
                                                          String expiryDate,
                                                          String cardBrand,
                                                          String line1,
                                                          String line2,
                                                          String city,
                                                          String county,
                                                          String postCode,
                                                          String countryCode) {
        return buildCorporateJsonAuthorisationDetailsFor(cardHolderName, cardNumber, cvc, expiryDate, cardBrand, line1,
                line2, city, county, postCode, countryCode, null, null, PREPAID_STATUS);
    }

    public static String buildCorporateJsonAuthorisationDetailsFor(PayersCardType payersCardType) {
        return buildCorporateJsonAuthorisationDetailsFor(
                CARD_HOLDER_NAME,
                CARD_NUMBER,
                CVC,
                EXPIRY_DATE,
                CARD_BRAND,
                ADDRESS_LINE_1,
                null,
                ADDRESS_CITY,
                null,
                ADDRESS_POSTCODE,
                ADDRESS_COUNTRY_GB,
                Boolean.TRUE,
                payersCardType,
                PREPAID_STATUS);
    }

    public static String buildJsonAuthorisationDetailsWithFullAddress() {
        return buildJsonAuthorisationDetailsFor(
                CARD_HOLDER_NAME,
                CARD_NUMBER,
                CVC,
                EXPIRY_DATE,
                CARD_BRAND,
                ADDRESS_LINE_1,
                ADDRESS_LINE_2,
                ADDRESS_CITY,
                ADDRESS_COUNTY,
                ADDRESS_POSTCODE,
                ADDRESS_COUNTRY_GB
        );
    }

    public static String buildJsonAuthorisationDetailsWithoutAddress() {
        JsonObject authorisationDetails = buildJsonAuthorisationDetailsWithoutAddress(
                CARD_HOLDER_NAME,
                CARD_NUMBER,
                CVC,
                EXPIRY_DATE,
                CARD_BRAND,
                Boolean.TRUE,
                PayersCardType.CREDIT,
                PREPAID_STATUS);

        return toJson(authorisationDetails);
    }

    private static String buildCorporateJsonAuthorisationDetailsFor(String cardHolderName,
                                                                    String cardNumber,
                                                                    String cvc,
                                                                    String expiryDate,
                                                                    String cardBrand,
                                                                    String line1,
                                                                    String line2,
                                                                    String city,
                                                                    String county,
                                                                    String postCode,
                                                                    String countryCode,
                                                                    Boolean isCorporateCard,
                                                                    PayersCardType payersCardType,
                                                                    PayersCardPrepaidStatus prepaidStatus) {

        JsonObject addressObject = buildAddressObject(line1, line2, city, county, postCode, countryCode);

        JsonObject authorisationDetails = buildJsonAuthorisationDetailsWithoutAddress(cardHolderName,
                cardNumber, cvc, expiryDate, cardBrand, isCorporateCard, payersCardType, prepaidStatus);

        authorisationDetails.add("address", addressObject);

        return toJson(authorisationDetails);
    }

    private static JsonObject buildJsonAuthorisationDetailsWithoutAddress(String cardHolderName,
                                                                          String cardNumber,
                                                                          String cvc,
                                                                          String expiryDate,
                                                                          String cardBrand,
                                                                          Boolean isCorporateCard,
                                                                          PayersCardType payersCardType,
                                                                          PayersCardPrepaidStatus prepaidStatus) {

        JsonObject authorisationDetails = new JsonObject();
        authorisationDetails.addProperty("card_number", cardNumber);
        authorisationDetails.addProperty("cvc", cvc);
        authorisationDetails.addProperty("expiry_date", expiryDate);
        authorisationDetails.addProperty("card_brand", cardBrand);
        authorisationDetails.addProperty("cardholder_name", cardHolderName);
        authorisationDetails.addProperty("accept_header", "text/html");
        authorisationDetails.addProperty("user_agent_header", "Mozilla/5.0");
        authorisationDetails.addProperty("prepaid", prepaidStatus.toString());

        if (isCorporateCard != null) {
            authorisationDetails.addProperty("corporate_card", isCorporateCard);
        }
        if (payersCardType != null) {
            authorisationDetails.addProperty("card_type", payersCardType.toString());
        }
        return authorisationDetails;
    }

    private static JsonObject buildAddressObject(String line1, String line2, String city, String county, String postCode, String countryCode) {

        JsonObject addressObject = new JsonObject();

        addressObject.addProperty("line1", line1);
        addressObject.addProperty("line2", line2);
        addressObject.addProperty("city", city);
        addressObject.addProperty("county", county);
        addressObject.addProperty("postcode", postCode);
        addressObject.addProperty("country", countryCode);
        return addressObject;
    }
}
