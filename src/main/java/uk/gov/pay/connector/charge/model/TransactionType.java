package uk.gov.pay.connector.charge.model;

import java.util.List;

public enum TransactionType {
    PAYMENT("payment"), REFUND("refund");

    private String value;

    TransactionType(String value) {
        this.value = value;
    }

    public static TransactionType inferTransactionTypeFrom(List<String> paymentStates, List<String> refundStates) {

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
