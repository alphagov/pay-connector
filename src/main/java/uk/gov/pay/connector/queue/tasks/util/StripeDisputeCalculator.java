package uk.gov.pay.connector.queue.tasks.util;

import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;
import uk.gov.pay.connector.queue.tasks.dispute.BalanceTransaction;

public class StripeDisputeCalculator {
    private StripeDisputeCalculator() {
    }

    public static long getNetAmountForLostDispute(StripeDisputeData stripeDisputeData) {
        long totalNet = stripeDisputeData.getBalanceTransactionList().stream().mapToLong(BalanceTransaction::getNetAmount).sum();
        if (totalNet > 0) {
            throw new RuntimeException("Expected total net amount for a lost dispute to be negative, but was positive");
        }
        return totalNet;
    }

    public static long getFeeForLostDispute(StripeDisputeData stripeDisputeData) {
        long totalFee = stripeDisputeData.getBalanceTransactionList().stream().mapToLong(BalanceTransaction::getFee).sum();
        if (totalFee < 0) {
            throw new RuntimeException("Expected total fee for a lost dispute to be positive, but was negative");
        }
        return totalFee;
    }
}
