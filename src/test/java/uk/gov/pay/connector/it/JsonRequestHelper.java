package uk.gov.pay.connector.it;

import com.google.gson.JsonObject;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class JsonRequestHelper {

    private static final String CVC = "123";
    private static final CardExpiryDate EXPIRY_DATE = CardExpiryDate.valueOf("11/99");
    private static final String CARD_HOLDER_NAME = "Scrooge McDuck";
    private static final String ADDRESS_LINE_1 = "The Money Pool";
    private static final String ADDRESS_CITY = "London";
    private static final String ADDRESS_POSTCODE = "DO11 4RS";
    private static final String ADDRESS_COUNTRY_GB = "GB";
    private static final String CARD_NUMBER = "4242424242424242";
    private static final String CARD_TYPE = "CREDIT";
    private static final String CARD_BRAND = "cardBrand";
    private static final String ADDRESS_LINE_2 = "Moneybags Avenue";
    private static final String ADDRESS_COUNTY = "Greater London";
    private static final PayersCardPrepaidStatus PREPAID_STATUS = PayersCardPrepaidStatus.NOT_PREPAID;

    public static String buildJsonAuthorisationDetailsFor(String cardNumber, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(cardNumber, CVC, EXPIRY_DATE.toString(), cardBrand);
    }

    public static String buildJsonAuthorisationDetailsFor(String cardHolderName, String cardNumber, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(cardHolderName, cardNumber, CVC, EXPIRY_DATE.toString(), cardBrand, CARD_TYPE, ADDRESS_LINE_1,
                null, ADDRESS_CITY, null, ADDRESS_POSTCODE, ADDRESS_COUNTRY_GB);
    }

    public static String buildJsonAuthorisationDetailsFor(String cardNumber, String cvc, String expiryDate, String cardBrand) {
        return buildJsonAuthorisationDetailsFor(CARD_HOLDER_NAME, cardNumber, cvc, expiryDate, cardBrand, CARD_TYPE, ADDRESS_LINE_1,
                null, ADDRESS_CITY, null, ADDRESS_POSTCODE, ADDRESS_COUNTRY_GB);
    }

    public static String buildDetailedJsonAuthorisationDetailsFor(String cardNumber,
                                                                  String cvc,
                                                                  String expiryDate,
                                                                  String cardBrand,
                                                                  String cardType,
                                                                  String cardHolderName,
                                                                  String addressLine1,
                                                                  String addressLine2,
                                                                  String city,
                                                                  String county,
                                                                  String postcode,
                                                                  String country) {
        return buildJsonAuthorisationDetailsFor(cardHolderName, cardNumber, cvc, expiryDate, cardBrand, cardType, addressLine1, addressLine2, city, county, postcode, country);
    }

    public static String buildJsonAuthorisationDetailsFor(String cardHolderName,
                                                          String cardNumber,
                                                          String cvc,
                                                          String expiryDate,
                                                          String cardBrand,
                                                          String cardType,
                                                          String line1,
                                                          String line2,
                                                          String city,
                                                          String county,
                                                          String postCode,
                                                          String countryCode) {
        return buildCorporateJsonAuthorisationDetailsFor(cardHolderName, cardNumber, cvc, expiryDate, cardBrand, line1,
                line2, city, county, postCode, countryCode, null, cardType, PREPAID_STATUS);
    }

    public static String buildCorporateJsonAuthorisationDetailsFor(PayersCardType payersCardType) {
        return buildCorporateJsonAuthorisationDetailsFor(
                CARD_HOLDER_NAME,
                CARD_NUMBER,
                CVC,
                EXPIRY_DATE.toString(),
                CARD_BRAND,
                ADDRESS_LINE_1,
                null,
                ADDRESS_CITY,
                null,
                ADDRESS_POSTCODE,
                ADDRESS_COUNTRY_GB,
                Boolean.TRUE,
                payersCardType.toString(),
                PREPAID_STATUS);
    }

    public static String buildJsonAuthorisationDetailsWithFullAddress() {
        return buildJsonAuthorisationDetailsFor(
                CARD_HOLDER_NAME,
                CARD_NUMBER,
                CVC,
                EXPIRY_DATE.toString(),
                CARD_BRAND,
                CARD_TYPE,
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
                EXPIRY_DATE.toString(),
                CARD_BRAND,
                Boolean.TRUE,
                PayersCardType.CREDIT.toString(),
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
                                                                    String payersCardType,
                                                                    PayersCardPrepaidStatus prepaidStatus) {

        JsonObject addressObject = buildAddressObject(line1, line2, city, county, postCode, countryCode);

        JsonObject authorisationDetails = buildJsonAuthorisationDetailsWithoutAddress(cardHolderName,
                cardNumber, cvc, expiryDate, cardBrand, isCorporateCard, payersCardType, prepaidStatus);

        authorisationDetails.add("address", addressObject);

        return toJson(authorisationDetails);
    }
    
    public static String buildJsonApplePayAuthorisationDetails(String cardHolderName, String email) {
        JsonObject header = new JsonObject();
        header.addProperty("publicKeyHash", "LbsUwAT6w1JV9tFXocU813TCHks+LSuFF0R/eBkrWnQ=");
        header.addProperty("ephemeralPublicKey", "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEMwliotf2ICjiMwREdqyHSilqZzuV2fZey86nBIDlTY8sNMJv9CPpL5/DKg4bIEMe6qaj67mz4LWdr7Er0Ld5qA==");
        header.addProperty("transactionId", "2686f5297f123ec7fd9d31074d43d201953ca75f098890375f13aed2737d92f2");
        header.addProperty("applicationData", "some");
        header.addProperty("wrappedKey", "some");

        JsonObject encryptedPaymentData = new JsonObject();
        encryptedPaymentData.addProperty("version", "EC_v1");
        encryptedPaymentData.addProperty("signature", "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");
        encryptedPaymentData.add("header", header);
        encryptedPaymentData.addProperty("data", "4OZho15e9Yp5K0EtKergKzeRpPAjnKHwmSNnagxhjwhKQ5d29sfTXjdbh1CtTJ4DYjsD6kfulNUnYmBTsruphBz7RRVI1WI8P0LrmfTnImjcq1mi+BRN7EtR2y6MkDmAr78anff91hlc+x8eWD/NpO/oZ1ey5qV5RBy/Jp5zh6ndVUVq8MHHhvQv4pLy5Tfi57Yo4RUhAsyXyTh4x/p1360BZmoWomK15NcJfUmoUCuwEYoi7xUkRwNr1z4MKnzMfneSRpUgdc0wADMeB6u1jcuwqQnnh2cusiagOTCfD6jO6tmouvu6KO54uU7bAbKz6cocIOEAOc6keyFXG5dfw8i3hJg6G2vIefHCwcKu1zFCHr4P7jLnYFDEhvxLm1KskDcuZeQHAkBMmLRSgj9NIcpBa94VN/JTga8W75IWAA==");

        JsonObject paymentInfo = new JsonObject();
        paymentInfo.addProperty("last_digits_card_number", "4242");
        paymentInfo.addProperty("brand", "visa");
        paymentInfo.addProperty("card_type", "DEBIT");
        paymentInfo.addProperty("cardholder_name", cardHolderName);
        paymentInfo.addProperty("email", email);
        paymentInfo.addProperty("display_name", "Visa 4242");
        paymentInfo.addProperty("transaction_identifier", "abc123");
        paymentInfo.addProperty("network", "Visa");

        JsonObject payload = new JsonObject();
        payload.add("payment_info", paymentInfo);
        payload.addProperty("payment_data", encryptedPaymentData.toString());
        return toJson(payload);
    }

    public static String buildJsonGooglePayAuthorisationDetails(String cardHolderName, String email) {
        JsonObject paymentInfo = new JsonObject();
        paymentInfo.addProperty("last_digits_card_number", "4242");
        paymentInfo.addProperty("brand", "visa");
        paymentInfo.addProperty("card_type", "DEBIT");
        paymentInfo.addProperty("cardholder_name", cardHolderName);
        paymentInfo.addProperty("email", email);
       
        JsonObject payload = new JsonObject();
        payload.add("payment_info", paymentInfo);
        payload.addProperty("token_id", "a-token-id");
        return toJson(payload);
    }

    public static String buildJsonForMotoApiPaymentAuthorisation(String cardHolderName,
                                                                 String cardNumber,
                                                                 String expiryDate,
                                                                 String cvc,
                                                                 String oneTimeToken) {
        JsonObject payload = new JsonObject();
        payload.addProperty("cardholder_name", cardHolderName);
        payload.addProperty("card_number", cardNumber);
        payload.addProperty("expiry_date", expiryDate);
        payload.addProperty("cvc", cvc);
        payload.addProperty("one_time_token", oneTimeToken);

        return toJson(payload);
    }

    private static JsonObject buildJsonAuthorisationDetailsWithoutAddress(String cardHolderName,
                                                                          String cardNumber,
                                                                          String cvc,
                                                                          String expiryDate,
                                                                          String cardBrand,
                                                                          Boolean isCorporateCard,
                                                                          String payersCardType,
                                                                          PayersCardPrepaidStatus prepaidStatus) {

        JsonObject authorisationDetails = new JsonObject();
        authorisationDetails.addProperty("card_number", cardNumber);
        authorisationDetails.addProperty("cvc", cvc);
        authorisationDetails.addProperty("expiry_date", expiryDate.toString());
        authorisationDetails.addProperty("card_brand", cardBrand);
        authorisationDetails.addProperty("cardholder_name", cardHolderName);
        authorisationDetails.addProperty("accept_header", "text/html");
        authorisationDetails.addProperty("user_agent_header", "Mozilla/5.0");
        authorisationDetails.addProperty("prepaid", prepaidStatus.toString());

        if (isCorporateCard != null) {
            authorisationDetails.addProperty("corporate_card", isCorporateCard);
        }
        if (payersCardType != null) {
            authorisationDetails.addProperty("card_type", payersCardType);
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
