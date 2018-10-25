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

    /**
     * Get the total amount available to be refunded. Will be the total amount, including any additional charges
     * minus the amount that has already been refunded.
     *
     * @param chargeEntity The {@link ChargeEntity} to get the amount available to be refunded for
     * @return long - amount available to be refunded
     */
    public static long getTotalAmountToBeRefunded(ChargeEntity chargeEntity) {
        return CorporateCardSurchargeCalculator.getTotalAmountFor(chargeEntity) - getRefundedAmount(chargeEntity);
    }

    /**
     * Get the total amount that has been refunded for the given {@link ChargeEntity}
     *
     * @param chargeEntity The {@link ChargeEntity} to get the amount refunded for
     * @return long - refunded amount
     */
    public static long getRefundedAmount(ChargeEntity chargeEntity) {
        return chargeEntity.getRefunds().stream()
                .filter(p -> p.hasStatus(RefundStatus.CREATED, RefundStatus.REFUND_SUBMITTED, RefundStatus.REFUNDED))
                .mapToLong(RefundEntity::getAmount)
                .sum();
    }

}
