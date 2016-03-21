package uk.gov.pay.connector.service.smartpay;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class SmartpayStatusMapper {

    private static final Map<String, Map<Boolean, ChargeStatus>> smartpayStatuses = ImmutableMap.<String, Map<Boolean, ChargeStatus>>builder().
            put("AUTHORISATION", ImmutableMap.of(true, AUTHORISATION_SUCCESS, false, AUTHORISATION_REJECTED)).
            put("CAPTURE", ImmutableMap.of(true, CAPTURED)).
            put("CANCELLATION", Collections.emptyMap()).
            put("REFUND", Collections.emptyMap()).
            put("REQUEST_FOR_INFORMATION", Collections.emptyMap()).
            put("NOTIFICATION_OF_CHARGEBACK", Collections.emptyMap()).
            put("CHARGEBACK", Collections.emptyMap()).
            put("CHARGEBACK_REVERSED", Collections.emptyMap()).
            put("REPORT_AVAILABLE", Collections.emptyMap()).
            build();

    public static Optional<ChargeStatus> mapToChargeStatus(String smartpayStatus, Boolean successFull) {
        return ofNullable(smartpayStatuses.get(smartpayStatus)).map(m -> m.get(successFull));
    }
}
