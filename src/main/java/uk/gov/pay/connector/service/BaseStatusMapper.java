package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import uk.gov.pay.connector.model.domain.DeferredStatusResolver;
import uk.gov.pay.connector.model.domain.InternalExternalStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BaseStatusMapper<V> implements StatusMapper<V> {

    public static class StatusMap<V> {
        private V value;
        private Optional<InternalExternalStatus> status = Optional.empty();
        private Optional<DeferredStatusResolver> deferredStatusResolver = Optional.empty();

        private StatusMap(V value) {
            this.value = value;
        }

        private StatusMap(V value, InternalExternalStatus status) {
            this.value = value;
            this.status = Optional.ofNullable(status);
        }

        private StatusMap(V value, DeferredStatusResolver deferredStatusResolver) {
            this.value = value;
            this.deferredStatusResolver = Optional.of(deferredStatusResolver);
        }

        public static <V> StatusMap of(V value, InternalExternalStatus status) {
            return new StatusMap(value, status);
        }

        public static <V> StatusMap of(V value) {
            return new StatusMap<>(value);
        }

        public V getValue() {
            return value;
        }

        public Optional<InternalExternalStatus> getStatus() {
            return status;
        }

        public Optional<DeferredStatusResolver> getDeferredStatusResolver() {
          return deferredStatusResolver;
        }

        public static <V> StatusMap of(V value, DeferredStatusResolver deferredStatusResolver) {
            return new StatusMap(value, deferredStatusResolver);
        }
    }

    public static class MappedStatus implements InterpretedStatus {
        private InternalExternalStatus status;

        public MappedStatus(InternalExternalStatus status) {
            this.status = status;
        }

        @Override
        public boolean isMapped() {
            return true;
        }

        public Optional<InternalExternalStatus> get() {
            return Optional.of(status);
        }
    }

    public static class UnknownStatus implements InterpretedStatus {
        @Override
        public boolean isUnknown() {
            return true;
        }
    }

    public static class IgnoredStatus implements InterpretedStatus {
        @Override
        public boolean isIgnored() {
            return true;
        }
    }

    public static class DeferredStatus implements InterpretedStatus {
        @Override
        public boolean isDeferred() { return true; }

        public DeferredStatusResolver getDeferredStatusResolver() {
            return  getDeferredStatusResolver();
        }
    }

    public static class Builder<V> {
        private List<StatusMap> validStatuses = new ArrayList<>();

        public Builder<V> map(V value, InternalExternalStatus status) {
            validStatuses.add(StatusMap.of(value, status));
            return this;
        }

        public Builder<V> ignore(V value) {
            validStatuses.add(StatusMap.of(value));
            return this;
        }

        public Builder<V> mapDeferred(V value, DeferredStatusResolver deferredStatusResolver) {
            validStatuses.add(StatusMap.of(value, deferredStatusResolver));
            return this;
        }

        public BaseStatusMapper<V> build() {
            return new BaseStatusMapper(ImmutableList.copyOf(validStatuses));
        }
    }

    private final List<StatusMap<V>> validStatuses;

    private BaseStatusMapper(List<StatusMap<V>> validStatuses) {
        this.validStatuses = validStatuses;
    }

    public static <V> Builder<V> builder() {
        return new Builder();
    }

    @Override
    public InterpretedStatus from(V value) {
        Optional<StatusMap<V>> statusMap = validStatuses
                .stream()
                .filter(p -> p.getValue().equals(value))
                .findFirst();

        if (!statusMap.isPresent()) {
            return new UnknownStatus();
        }

        Optional<InternalExternalStatus> status = statusMap.flatMap(StatusMap::getStatus);
        Optional<DeferredStatusResolver> deferredStatusResolver = statusMap.flatMap(StatusMap::getDeferredStatusResolver);

        if (!status.isPresent() && !deferredStatusResolver.isPresent()) {
            return new IgnoredStatus();
        }

        if (statusMap.get().deferredStatusResolver.isPresent()) {
            return new DeferredStatus();
        }

        return new MappedStatus(status.get());
    }
}
