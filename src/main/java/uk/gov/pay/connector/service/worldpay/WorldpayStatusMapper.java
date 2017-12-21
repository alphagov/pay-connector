package uk.gov.pay.connector.service.worldpay;

import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.model.GatewayStatusOnly;
import uk.gov.pay.connector.model.GatewayStatusWithCurrentStatus;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.service.IgnoredStatus;
import uk.gov.pay.connector.service.InterpretedStatus;
import uk.gov.pay.connector.service.MappedChargeStatus;
import uk.gov.pay.connector.service.MappedRefundStatus;
import uk.gov.pay.connector.service.StatusMapper;
import uk.gov.pay.connector.service.UnknownStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;

public class WorldpayStatusMapper {

    private static final Map<String, InterpretedStatus> map = new ImmutableMap.Builder<String, InterpretedStatus>()
            .put("SENT_FOR_AUTHORISATION", new IgnoredStatus())
            .put("AUTHORISED", new IgnoredStatus())
            .put("CANCELLED", new IgnoredStatus())
            .put("EXPIRED", new IgnoredStatus())
            .put("REFUSED", new IgnoredStatus())
            .put("REFUSED_BY_BANK", new IgnoredStatus())
            .put("SETTLED_BY_MERCHANT", new IgnoredStatus())
            .put("SENT_FOR_REFUND", new IgnoredStatus())
            .put("CAPTURED", new MappedChargeStatus(CAPTURED))
            .put("REFUNDED", new MappedRefundStatus(REFUNDED))
            .put("REFUNDED_BY_MERCHANT", new MappedRefundStatus(REFUNDED))
            .put("REFUND_FAILED", new MappedRefundStatus(REFUND_ERROR))
            .build();

    public static boolean ignored(String gatewayStatus) {
        return from(gatewayStatus).getType() == InterpretedStatus.Type.IGNORED;
    }

    public static ChargeStatus chargeStatus(String gatewayStatus) {
        InterpretedStatus to = from(gatewayStatus);
        if (to.getType() == InterpretedStatus.Type.CHARGE_STATUS) {
            return to.getChargeStatus();
        }
        return null;
    }

    public static RefundStatus refundStatus(String gatewayStatus) {
        InterpretedStatus to = from(gatewayStatus);
        if (to.getType() == InterpretedStatus.Type.REFUND_STATUS) {
            return to.getRefundStatus();
        }
        return null;
    }

    public static InterpretedStatus from(String gatewayStatus) {
        return map.getOrDefault(gatewayStatus, new UnknownStatus());
    }
}
