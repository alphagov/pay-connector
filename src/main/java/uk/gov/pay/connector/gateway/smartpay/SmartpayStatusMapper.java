package uk.gov.pay.connector.gateway.smartpay;

import com.google.common.collect.ImmutableList;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.Status;
import uk.gov.pay.connector.gateway.model.status.IgnoredStatus;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedChargeStatus;
import uk.gov.pay.connector.gateway.model.status.MappedRefundStatus;
import uk.gov.pay.connector.gateway.model.status.UnknownStatus;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;

public class SmartpayStatusMapper {

    private static final List<StatusMap> validStatuses = new ImmutableList.Builder<StatusMap>()
            .add(new StatusMap(new SmartpayStatus("AUTHORISATION", true)))
            .add(new StatusMap(new SmartpayStatus("AUTHORISATION", false)))
            .add(new StatusMap(new SmartpayStatus("CAPTURE", true), CAPTURED))
            .add(new StatusMap(new SmartpayStatus("CAPTURE", false), CAPTURE_ERROR))
            .add(new StatusMap(new SmartpayStatus("CANCELLATION", true)))
            .add(new StatusMap(new SmartpayStatus("CANCELLATION", false)))
            .add(new StatusMap(new SmartpayStatus("REFUND", true), REFUNDED))
            .add(new StatusMap(new SmartpayStatus("REFUND", false), REFUND_ERROR))
            .add(new StatusMap(new SmartpayStatus("REFUND_FAILED", true), REFUND_ERROR))    // TODO:: Check with Smartpay what is a valid business use case
            .add(new StatusMap(new SmartpayStatus("REFUND_FAILED", false), REFUND_ERROR))
            .add(new StatusMap(new SmartpayStatus("REFUND", false)))
            .add(new StatusMap(new SmartpayStatus("REQUEST_FOR_INFORMATION", true)))
            .add(new StatusMap(new SmartpayStatus("REQUEST_FOR_INFORMATION", false)))
            .add(new StatusMap(new SmartpayStatus("NOTIFICATION_OF_CHARGEBACK", true)))
            .add(new StatusMap(new SmartpayStatus("NOTIFICATION_OF_CHARGEBACK", false)))
            .add(new StatusMap(new SmartpayStatus("CHARGEBACK", true)))
            .add(new StatusMap(new SmartpayStatus("CHARGEBACK", false)))
            .add(new StatusMap(new SmartpayStatus("CHARGEBACK_REVERSED", true)))
            .add(new StatusMap(new SmartpayStatus("CHARGEBACK_REVERSED", false)))
            .add(new StatusMap(new SmartpayStatus("REPORT_AVAILABLE", true)))
            .add(new StatusMap(new SmartpayStatus("REPORT_AVAILABLE", false)))
            .build();

    public static InterpretedStatus from(SmartpayStatus gatewayStatus) {

        Optional<StatusMap> statusMap = validStatuses
                .stream()
                .filter(validStatus -> validStatus.getFromStatus().equals(gatewayStatus))
                .findFirst();

        if (statusMap.isEmpty()) {
            return new UnknownStatus();
        }

        return statusMap
                .flatMap(StatusMap::getToStatus)
                .map(status -> {
                    if (status instanceof ChargeStatus) {
                        return new MappedChargeStatus((ChargeStatus) status);
                    }
                    if (status instanceof RefundStatus) {
                        return new MappedRefundStatus((RefundStatus) status);
                    }
                    return new UnknownStatus();
                })
                .orElseGet(IgnoredStatus::new);
    }

    private static class StatusMap {

        private final SmartpayStatus fromStatus;
        private Status toStatus;

        private StatusMap(SmartpayStatus gatewayStatus) {
            this.fromStatus = gatewayStatus;
        }

        private StatusMap(SmartpayStatus fromStatus, Status toStatus) {
            this.fromStatus = Objects.requireNonNull(fromStatus);
            this.toStatus = toStatus;
        }

        public SmartpayStatus getFromStatus() {
            return fromStatus;
        }

        public Optional<Status> getToStatus() {
            return Optional.ofNullable(toStatus);
        }
    }
}
