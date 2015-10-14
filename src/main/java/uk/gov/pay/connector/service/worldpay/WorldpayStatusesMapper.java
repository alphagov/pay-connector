package uk.gov.pay.connector.service.worldpay;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class WorldpayStatusesMapper {

    private static final Map<String, ChargeStatus> worldpayStatuses = ImmutableMap.<String, ChargeStatus>builder()
                .put("SENT_FOR_AUTHORISATION", AUTHORISATION_SUBMITTED)
                .put("AUTHORISED", AUTHORISATION_SUCCESS)
                .put("CANCELLED", SYSTEM_CANCELLED)
                .put("CAPTURED", CAPTURED)
                .put("REFUSED", AUTHORISATION_REJECTED)
                .put("REFUSED_BY_BANK", AUTHORISATION_REJECTED).build();

//    "SIGNED_FORM_RECEIVED"
//    "CAPTURED_FAILED"
//    "ERROR"
//    "CHARGED_BACK"
//    "SETTLED"
//    "SETTLED_BY_MERCHANT"
//    "SENT_FOR_REFUND"
//    "REFUND_FAILED"
//    "REFUNDED"
//    "REFUNDED_BY_MERCHANT"
//    "EXPIRED"
//    "INFORMATION_REQUESTED"
//    "INFORMATION_SUPPLIED"
//    "CHARGEBACK_REVERSED"
//    "REFUND_WEBFORM_ISSUED"
//    "REVOKE_REQUESTED"
//    "REVOKE_FAILED"
//    "REVOKED"

    public static Optional<ChargeStatus> mapToChargeStatus(String worldpayStatus) {
        return ofNullable(worldpayStatuses.get(worldpayStatus));
    }


}
