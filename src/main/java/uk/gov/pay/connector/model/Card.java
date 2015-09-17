package uk.gov.pay.connector.model;

import java.util.Map;

import static uk.gov.pay.connector.model.Address.anAddress;

public class Card {


    public static final String FIELD_CVC = "cvc";
    public static final String FIELD_EXPIRY_DATE = "expiry_date";
    public static final String FIELD_CARD_NUMBER = "card_number";
    public static final String FIELD_CARDHOLDER_NAME = "cardholder_name";
    public static final String FIELD_ADDRESS_LINE1 = "line1";
    public static final String FIELD_ADDRESS_LINE2 = "line2";
    public static final String FIELD_ADDRESS_LINE3 = "line3";
    public static final String FIELD_ADDRESS_POSTCODE = "postcode";
    public static final String FIELD_ADDRESS_CITY = "city";
    public static final String FIELD_ADDRESS_COUNTY = "county";
    public static final String FIELD_ADDRESS_COUNTRY = "country";

    private String cardNo;
    private String cardHolder;
    private String cvc;
    private String endDate;

    private Address address;

    public static Card aCard() {
        return new Card();
    }

    public static Card getCardFromDetails(Map<String, Object> cardDetails) {
        String cvc = (String) cardDetails.get(FIELD_CVC);
        String expiryDate = (String) cardDetails.get(FIELD_EXPIRY_DATE);
        String cardNo = (String) cardDetails.get(FIELD_CARD_NUMBER);
        String cardHoderName = (String) cardDetails.get(FIELD_CARDHOLDER_NAME);

        Address address = getAddress((Map<String, Object>) cardDetails.get("address"));

        return aCard()
                .withCardDetails(cardHoderName, cardNo, cvc, expiryDate)
                .withAddress(address);
    }

    private static Address getAddress(Map<String, Object> cardDetails) {
        if (cardDetails == null) {
            return null;
        }
        return anAddress().withLine1((String) cardDetails.get(FIELD_ADDRESS_LINE1))
                .withLine2((String) cardDetails.get(FIELD_ADDRESS_LINE2))
                .withLine2((String) cardDetails.get(FIELD_ADDRESS_LINE3))
                .withZip((String) cardDetails.get(FIELD_ADDRESS_POSTCODE))
                .withCity((String) cardDetails.get(FIELD_ADDRESS_CITY))
                .withCounty((String) cardDetails.get(FIELD_ADDRESS_COUNTY))
                .withCountry((String) cardDetails.get(FIELD_ADDRESS_COUNTRY));
    }

    public Card withCardDetails(String cardHolder, String cardNo, String cvc, String endDate) {
        this.cardHolder = cardHolder;
        this.cardNo = cardNo;
        this.cvc = cvc;
        this.endDate = endDate;
        return this;
    }

    public Card withAddress(Address address) {
        this.address = address;
        return this;
    }

    public String getCardNo() {
        return cardNo;
    }

    public String getCardHolder() {
        return cardHolder;
    }

    public String getCvc() {
        return cvc;
    }

    public String getEndDate() {
        return endDate;
    }

    public Address getAddress() {
        return address;
    }
}
