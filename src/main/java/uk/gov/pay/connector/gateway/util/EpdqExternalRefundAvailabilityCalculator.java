package uk.gov.pay.connector.gateway.util;

import com.google.common.collect.ImmutableList;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.util.List;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class EpdqExternalRefundAvailabilityCalculator extends DefaultExternalRefundAvailabilityCalculator {

    private static final List<ChargeStatus> STATUSES_THAT_MAP_TO_EXTERNAL_PENDING = ImmutableList.of(
            CREATED,
            ENTERING_CARD_DETAILS,
            AUTHORISATION_READY,
            AUTHORISATION_3DS_REQUIRED,
            AUTHORISATION_3DS_READY,
            AUTHORISATION_SUBMITTED,
            AUTHORISATION_SUCCESS,
            CAPTURE_READY,
            CAPTURE_APPROVED,
            CAPTURE_APPROVED_RETRY
    );

    private static final List<ChargeStatus> STATUSES_THAT_MAP_TO_EXTERNAL_AVAILABLE_OR_EXTERNAL_FULL = ImmutableList.of(
            CAPTURE_SUBMITTED,
            CAPTURED);

    @Override
    public ExternalChargeRefundAvailability calculate(ChargeEntity chargeEntity) {
        return calculate(chargeEntity, STATUSES_THAT_MAP_TO_EXTERNAL_PENDING, STATUSES_THAT_MAP_TO_EXTERNAL_AVAILABLE_OR_EXTERNAL_FULL);
    }

}
