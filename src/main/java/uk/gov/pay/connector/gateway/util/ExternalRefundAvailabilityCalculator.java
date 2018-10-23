package uk.gov.pay.connector.gateway.util;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;

public interface ExternalRefundAvailabilityCalculator {

    ExternalChargeRefundAvailability calculate(ChargeEntity chargeEntity);

}
