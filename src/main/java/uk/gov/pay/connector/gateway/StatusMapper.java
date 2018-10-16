package uk.gov.pay.connector.gateway;

import com.google.common.collect.ImmutableList;
import uk.gov.pay.connector.gateway.model.status.GatewayStatusOnly;
import uk.gov.pay.connector.gateway.model.status.GatewayStatusWithCurrentStatus;
import uk.gov.pay.connector.gateway.model.status.InterpretedStatus;
import uk.gov.pay.connector.gateway.model.status.MappedChargeStatus;
import uk.gov.pay.connector.gateway.model.status.MappedRefundStatus;
import uk.gov.pay.connector.gateway.model.status.StatusMapFromStatus;
import uk.gov.pay.connector.gateway.model.status.UnknownStatus;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.Status;
import uk.gov.pay.connector.gateway.model.status.IgnoredStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class StatusMapper<T> {

    public static class StatusMap<T> {

        private final StatusMapFromStatus<T> fromStatus;
        private Status toStatus;

        private StatusMap(GatewayStatusOnly<T> gatewayStatus) {
            this.fromStatus = gatewayStatus;
        }

        private StatusMap(StatusMapFromStatus<T> fromStatus, Status toStatus) {
            this.fromStatus = Objects.requireNonNull(fromStatus);
            this.toStatus = toStatus;
        }

        public static <T> StatusMap of(StatusMapFromStatus<T> fromStatus, Status toStatus) {
            return new StatusMap(fromStatus, toStatus);
        }

        public static <T> StatusMap of(GatewayStatusOnly<T> gatewayStatusOnly) {
            return new StatusMap<>(gatewayStatusOnly);
        }

        public StatusMapFromStatus<T> getFromStatus() {
            return fromStatus;
        }

        public Optional<Status> getToStatus() {
            return Optional.ofNullable(toStatus);
        }
    }

    public static class Builder<T> {
        private List<StatusMap<T>> validStatuses = new ArrayList<>();

        public Builder<T> map(T gatewayStatus, ChargeStatus currentStatus, Status status) {
            validStatuses.add(StatusMap.of(GatewayStatusWithCurrentStatus.of(gatewayStatus, currentStatus), status));
            return this;
        }

        public Builder<T> map(T gatewayStatus, Status status) {
            validStatuses.add(StatusMap.of(GatewayStatusOnly.of(gatewayStatus), status));
            return this;
        }

        public Builder<T> ignore(T gatewayStatus) {
            validStatuses.add(StatusMap.of(GatewayStatusOnly.of(gatewayStatus)));
            return this;
        }

        public StatusMapper<T> build() {
            return new StatusMapper(ImmutableList.copyOf(validStatuses));
        }
    }

    private final List<StatusMap<T>> validStatuses;

    private StatusMapper(List<StatusMap<T>> validStatuses) {
        this.validStatuses = validStatuses;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public InterpretedStatus from(T gatewayStatus, ChargeStatus currentStatus) {
        return from(GatewayStatusWithCurrentStatus.of(gatewayStatus, currentStatus), validStatuses);
    }

    public InterpretedStatus from(T gatewayStatus) {
        List<StatusMap<T>> gatewayStatusesOnly = this.validStatuses
                .stream()
                .filter(validStatus -> validStatus.getFromStatus() instanceof GatewayStatusOnly)
                .collect(Collectors.toList());
        return from(GatewayStatusOnly.of(gatewayStatus), gatewayStatusesOnly);
    }

    private InterpretedStatus from(StatusMapFromStatus<T> gatewayStatus, List<StatusMap<T>> wantedValidStatuses) {

        Optional<StatusMap<T>> statusMap = wantedValidStatuses
                .stream()
                .filter(validStatus -> validStatus.getFromStatus().equals(gatewayStatus))
                .findFirst();

        if (!statusMap.isPresent()) {
            return new UnknownStatus();
        }

        Optional<Status> statusMaybe = statusMap.flatMap(StatusMap::getToStatus);

        if (!statusMaybe.isPresent()) {
            return new IgnoredStatus();
        }

        Status status = statusMaybe.get();

        if (status instanceof ChargeStatus) {
            return new MappedChargeStatus((ChargeStatus) status);
        }

        if (status instanceof RefundStatus) {
            return new MappedRefundStatus((RefundStatus) status);
        }

        return new UnknownStatus();
    }
}
