package uk.gov.pay.connector.model;

import uk.gov.pay.connector.resources.CommaDelimitedSetParameter;

public enum TransactionType {
    PAYMENT("payment"), REFUND("refund");

    private String value;

    TransactionType(String value) {
        this.value = value;
    }

    public static TransactionType inferTransactionTypeFrom(CommaDelimitedSetParameter paymentStates, CommaDelimitedSetParameter refundStates) {

        TransactionType transactionType = null;
        boolean hasSpecifiedPaymentStates = paymentStates != null && !paymentStates.isEmpty();
        boolean hasSpecifiedRefundStates = refundStates != null && !refundStates.isEmpty();

        if (hasSpecifiedPaymentStates && !hasSpecifiedRefundStates) {
            transactionType = PAYMENT;
        } else if (hasSpecifiedRefundStates && !hasSpecifiedPaymentStates) {
            transactionType = REFUND;
        }

        return transactionType;
    }

    public String getValue() {
        return value;
    }
}
