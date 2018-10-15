package uk.gov.pay.connector.gateway.util;

import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.domain.ChargeEntity;

public interface ExternalRefundAvailabilityCalculator {

    ExternalChargeRefundAvailability calculate(ChargeEntity chargeEntity);

}
