package uk.gov.pay.connector.charge.model.domain;

public enum TransactionType {

    CHARGE("charge"), REFUND("refund");

    private String value;

    TransactionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TransactionType fromString(String value) {
        for (TransactionType transactionType : values()) {
            if (transactionType.getValue().equalsIgnoreCase(value)) {
                return transactionType;
            }
        }
        throw new IllegalArgumentException("transaction type not recognized: " + value);
    }

    @Override
    public String toString() {
        return this.getValue();
    }

}
