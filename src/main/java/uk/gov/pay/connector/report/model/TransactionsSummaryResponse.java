package uk.gov.pay.connector.report.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class TransactionsSummaryResponse {

    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    public static class SuccessfulPayments {

        @JsonProperty("count")
        private int count;

        @JsonProperty("total_in_pence")
        private long totalInPence;

        SuccessfulPayments(int count, long totalInPence) {
            this.count = count;
            this.totalInPence = totalInPence;
        }

        public int getCount() {
            return count;
        }

        public long getTotalInPence() {
            return totalInPence;
        }

    }

    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    public static class RefundedPayments {

        @JsonProperty("count")
        private int count;

        @JsonProperty("total_in_pence")
        private long totalInPence;

        RefundedPayments(int count, long totalInPence) {
            this.count = count;
            this.totalInPence = totalInPence;
        }

        public int getCount() {
            return count;
        }

        public long getTotalInPence() {
            return totalInPence;
        }

    }

    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    public static class NetIncome {

        @JsonProperty("total_in_pence")
        private long totalInPence;

        NetIncome(long totalInPence) {
            this.totalInPence = totalInPence;
        }

        public long getTotalInPence() {
            return totalInPence;
        }

    }

    @JsonProperty("successful_payments")
    private SuccessfulPayments successfulPayments;

    @JsonProperty("refunded_payments")
    private RefundedPayments refundedPayments;

    @JsonProperty("net_income")
    private NetIncome netIncome;

    public TransactionsSummaryResponse(int successfulPaymentsCount, long successfulPaymentsTotalInPence,
                                       int refundedPaymentsCount, long refundedPaymentsTotalInPence,
                                       long netIncomeTotalInPence) {
        this.successfulPayments = new SuccessfulPayments(successfulPaymentsCount, successfulPaymentsTotalInPence);
        this.refundedPayments = new RefundedPayments(refundedPaymentsCount, refundedPaymentsTotalInPence);
        this.netIncome = new NetIncome(netIncomeTotalInPence);
    }

    public SuccessfulPayments getSuccessfulPayments() {
        return successfulPayments;
    }

    public RefundedPayments getRefundedPayments() {
        return refundedPayments;
    }

    public NetIncome getNetIncome() {
        return netIncome;
    }

}
