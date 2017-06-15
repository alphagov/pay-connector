package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import uk.gov.pay.connector.model.ProviderStatusOnly;
import uk.gov.pay.connector.model.ProviderStatusWithCurrentStatus;
import uk.gov.pay.connector.model.StatusMapFromStatus;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.Status;
import uk.gov.pay.connector.model.domain.RefundStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BaseStatusMapper<T> implements StatusMapper<T> {

    public static class StatusMap<T> {

        private final StatusMapFromStatus<T> fromStatus;
        private Status toStatus;

        private StatusMap(ProviderStatusOnly<T> providerStatus) {
            this.fromStatus = providerStatus;
        }

        private StatusMap(StatusMapFromStatus<T> fromStatus, Status toStatus) {
            this.fromStatus = Objects.requireNonNull(fromStatus);
            this.toStatus = toStatus;
        }

        public static <T> StatusMap of(StatusMapFromStatus<T> fromStatus, Status toStatus) {
            return new StatusMap(fromStatus, toStatus);
        }

        public static <T> StatusMap of(ProviderStatusOnly<T> providerStatusOnly) {
            return new StatusMap<>(providerStatusOnly);
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

        public Builder<T> map(T providerStatus, ChargeStatus currentStatus, Status status) {
            validStatuses.add(StatusMap.of(ProviderStatusWithCurrentStatus.of(providerStatus, currentStatus), status));
            return this;
        }

        public Builder<T> map(T providerStatus, Status status) {
            validStatuses.add(StatusMap.of(ProviderStatusOnly.of(providerStatus), status));
            return this;
        }

        public Builder<T> ignore(T providerStatus) {
            validStatuses.add(StatusMap.of(ProviderStatusOnly.of(providerStatus)));
            return this;
        }

        public BaseStatusMapper<T> build() {
            return new BaseStatusMapper(ImmutableList.copyOf(validStatuses));
        }
    }

    private final List<StatusMap<T>> validStatuses;

    private BaseStatusMapper(List<StatusMap<T>> validStatuses) {
        this.validStatuses = validStatuses;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public InterpretedStatus from(T providerStatus, ChargeStatus currentStatus) {
        ProviderStatusWithCurrentStatus providerStatusWithCurrentStatus = ProviderStatusWithCurrentStatus.of(providerStatus, currentStatus);
        Optional<StatusMap<T>> statusMap = validStatuses
                .stream()
                .filter(validStatus -> validStatus.getFromStatus().equals(providerStatusWithCurrentStatus))
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
