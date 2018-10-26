package uk.gov.pay.connector.charge.util;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

/**
 * Holder for utility methods used to calculate refund amounts
 */
public class RefundCalculator {

    private RefundCalculator() {
        // prevent Java for adding a public constructor
    }

    public static long getTotalAmountAvailableToBeRefunded(ChargeEntity chargeEntity) {
        return CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity) - getRefundedAmount(chargeEntity);
    }

    public static long getRefundedAmount(ChargeEntity chargeEntity) {
        return chargeEntity.getRefunds().stream()
                .filter(p -> p.hasStatus(RefundStatus.CREATED, RefundStatus.REFUND_SUBMITTED, RefundStatus.REFUNDED))
                .mapToLong(RefundEntity::getAmount)
                .sum();
    }

}
