package uk.gov.pay.connector.model.domain;

public class Amount {
    private static final String DEFAULT_CURRENCY_CODE = "GBP";
    private static final String DEFAULT_EXPONENT = "2";
    private final String value;

    public Amount(String value) {
        this.value = value;
    }

    public String getCurrencyCode() {
        return DEFAULT_CURRENCY_CODE;
    }

    public String getExponent() {
        return DEFAULT_EXPONENT;
    }

    public String getValue() {
        return value;
    }
}
