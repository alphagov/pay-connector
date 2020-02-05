package uk.gov.pay.connector.charge.util;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
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

    public static long getTotalAmountAvailableToBeRefunded(ChargeEntity chargeEntity, List<RefundEntity> refundEntities) {
        return CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity) - getRefundedAmount(refundEntities);
    }

    public static long getRefundedAmount(List<RefundEntity> refundEntities) {
        return refundEntities.stream()
                .filter(p -> p.hasStatus(RefundStatus.CREATED, RefundStatus.REFUND_SUBMITTED, RefundStatus.REFUNDED))
                .mapToLong(RefundEntity::getAmount)
                .sum();
    }

}
