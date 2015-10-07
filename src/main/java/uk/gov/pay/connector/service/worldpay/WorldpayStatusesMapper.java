package uk.gov.pay.connector.service.worldpay;

import com.google.common.collect.Maps;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.HashMap;
import java.util.Map;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCELLED;

public class WorldpayStatusesMapper {

    //TODO: immutable
    private static final Map<String, ChargeStatus> worldpayStatuses = new HashMap<String, ChargeStatus>() {{
                put("SENT_FOR_AUTHORISATION", AUTHORISATION_SUBMITTED);
                put("AUTHORISED", AUTHORISATION_SUCCESS);
                put("CANCELLED", SYSTEM_CANCELLED); //???
                put("CAPTURED", CAPTURED);
                put("REFUSED", AUTHORISATION_REJECTED);
                put("REFUSED_BY_BANK", AUTHORISATION_REJECTED);
            }};

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

    public static ChargeStatus mapToChargeStatus(String worldpayStatus) {
        return worldpayStatuses.get(worldpayStatus);
    }


}
