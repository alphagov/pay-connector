package uk.gov.pay.connector.charge.model;

import java.util.List;

public enum TransactionSearchStrategyTransactionType {
    PAYMENT("payment"), REFUND("refund");

    private String value;

    TransactionSearchStrategyTransactionType(String value) {
        this.value = value;
    }

    public static TransactionSearchStrategyTransactionType inferTransactionTypeFrom(List<String> paymentStates, List<String> refundStates) {

        TransactionSearchStrategyTransactionType transactionSearchStrategyTransactionType = null;
        boolean hasSpecifiedPaymentStates = paymentStates != null && !paymentStates.isEmpty();
        boolean hasSpecifiedRefundStates = refundStates != null && !refundStates.isEmpty();

        if (hasSpecifiedPaymentStates && !hasSpecifiedRefundStates) {
            transactionSearchStrategyTransactionType = PAYMENT;
        } else if (hasSpecifiedRefundStates && !hasSpecifiedPaymentStates) {
            transactionSearchStrategyTransactionType = REFUND;
        }

        return transactionSearchStrategyTransactionType;
    }

    public String getValue() {
        return value;
    }
}
