package uk.gov.pay.connector.gateway.util;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.refund.model.domain.Refund;

import java.util.List;

public interface ExternalRefundAvailabilityCalculator {

    ExternalChargeRefundAvailability calculate(Charge charge, List<Refund> refundEntityList);
}
