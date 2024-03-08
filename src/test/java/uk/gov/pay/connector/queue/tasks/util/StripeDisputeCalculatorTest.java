package uk.gov.pay.connector.queue.tasks.util;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.gateway.stripe.json.StripeDisputeData;
import uk.gov.pay.connector.queue.tasks.dispute.BalanceTransaction;
import uk.gov.pay.connector.queue.tasks.dispute.EvidenceDetails;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

class StripeDisputeCalculatorTest {

    @Test
    void shouldReturnNetAmountForSingleBalanceTransaction() {
        BalanceTransaction balanceTransaction = new BalanceTransaction(-6500L, 1500L, -8000L);
        var balanceTransactionList = List.of(balanceTransaction);
        StripeDisputeData stripeDisputeData = getStripeDisputeData(balanceTransactionList);

        assertThat(StripeDisputeCalculator.getNetAmountForLostDispute(stripeDisputeData), is(-8000L));
    }
    
    @Test
    void shouldReturnNetAmountForMultipleBalanceTransactions() {
        BalanceTransaction balanceTransaction1 = new BalanceTransaction(-6500L, 1500L, -8000L);
        BalanceTransaction balanceTransaction2 = new BalanceTransaction(-100L, 0L, -100L);
        BalanceTransaction balanceTransaction3 = new BalanceTransaction(10L, 0L, 10L);
        var balanceTransactionList = List.of(balanceTransaction1, balanceTransaction2, balanceTransaction3);
        StripeDisputeData stripeDisputeData = getStripeDisputeData(balanceTransactionList);

        assertThat(StripeDisputeCalculator.getNetAmountForLostDispute(stripeDisputeData), is(-8090L));
    }

    @Test
    void shouldThrowWhenNetAmountIsPositive() {
        BalanceTransaction balanceTransaction = new BalanceTransaction(6500L, 0L, 6500L);
        var balanceTransactionList = List.of(balanceTransaction);
        StripeDisputeData stripeDisputeData = getStripeDisputeData(balanceTransactionList);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> StripeDisputeCalculator.getNetAmountForLostDispute(stripeDisputeData));
        assertThat(exception.getMessage(), is("Expected total net amount for a lost dispute to be negative, but was positive"));
    }

    @Test
    void shouldReturnFeeForSingleBalanceTransaction() {
        BalanceTransaction balanceTransaction = new BalanceTransaction(-6500L, 1500L, -8000L);
        var balanceTransactionList = List.of(balanceTransaction);
        StripeDisputeData stripeDisputeData = getStripeDisputeData(balanceTransactionList);

        assertThat(StripeDisputeCalculator.getFeeForLostDispute(stripeDisputeData), is(1500L));
    }

    @Test
    void shouldReturnFeeForMultipleBalanceTransactions() {
        BalanceTransaction balanceTransaction1 = new BalanceTransaction(-6500L, 1500L, -8000L);
        BalanceTransaction balanceTransaction2 = new BalanceTransaction(-100L, 1L, -100L);
        var balanceTransactionList = List.of(balanceTransaction1, balanceTransaction2);
        StripeDisputeData stripeDisputeData = getStripeDisputeData(balanceTransactionList);

        assertThat(StripeDisputeCalculator.getFeeForLostDispute(stripeDisputeData), is(1501L));
    }

    @Test
    void shouldThrowWhenFeeIsNegative() {
        BalanceTransaction balanceTransaction = new BalanceTransaction(-6500L, -10L, -6500L);
        var balanceTransactionList = List.of(balanceTransaction);
        StripeDisputeData stripeDisputeData = getStripeDisputeData(balanceTransactionList);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> StripeDisputeCalculator.getFeeForLostDispute(stripeDisputeData));
        assertThat(exception.getMessage(), is("Expected total fee for a lost dispute to be positive, but was negative"));
    }
    
    private static StripeDisputeData getStripeDisputeData(List<BalanceTransaction> balanceTransactionList) {
        StripeDisputeData stripeDisputeData = new StripeDisputeData("du_1LIaq8Dv3CZEaFO2MNQJK333",
                "pi_123456789", "needs_response", 6500L, "fraudulent",
                1642579160L, balanceTransactionList, new EvidenceDetails(1642679160L),
                null, false);
        return stripeDisputeData;
    }

}
