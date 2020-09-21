package uk.gov.pay.connector.charge.util;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.List;

/**
 * Holder for utility methods used to calculate refund amounts
 */
public class RefundCalculator {

    private RefundCalculator() {
        // prevent Java for adding a public constructor
    }

    public static long getTotalAmountAvailableToBeRefunded(Charge charge, List<Refund> refundList) {
        return CorporateCardSurchargeCalculator.getTotalAmountFor(charge) - getRefundedAmount(refundList);
    }

    public static long getTotalAmountAvailableToBeRefunded(ChargeEntity chargeEntity, List<Refund> refundList) {
        return CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity) - getRefundedAmount(refundList);
    }

    public static long getRefundedAmount(List<Refund> refundList) {
        return refundList.stream()
                .filter(p -> List.of(RefundStatus.CREATED, RefundStatus.REFUND_SUBMITTED, RefundStatus.REFUNDED).contains(p.getStatus()))
                .mapToLong(Refund::getAmount)
                .sum();
    }

}
