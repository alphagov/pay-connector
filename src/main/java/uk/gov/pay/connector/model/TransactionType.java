package uk.gov.pay.connector.model;

public enum TransactionType {
    PAYMENT("payment"), REFUND("refund");

    private String value;

    TransactionType(String value) {
        this.value = value;
    }

    public static TransactionType valueFrom(String value) {
        for (TransactionType transactionType : values()) {
            if (transactionType.value.equals(value)) {
                return transactionType;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }
}
