package uk.gov.pay.connector.charge.util;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.refund.model.domain.Refund;

import java.util.List;

import static uk.gov.pay.connector.common.model.api.ExternalRefundStatus.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.common.model.api.ExternalRefundStatus.EXTERNAL_SUCCESS;

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
                .filter(p -> List.of(EXTERNAL_SUBMITTED, EXTERNAL_SUCCESS).contains(p.getExternalStatus()))
                .mapToLong(Refund::getAmount)
                .sum();
    }

}
