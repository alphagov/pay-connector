package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BaseStatusMapper<V> implements StatusMapper<V> {

    public static class StatusMap<V> {
        private V value;
        private Optional<Enum> status = Optional.empty();

        private StatusMap(V value, Enum status) {
            this.value = value;
            this.status = Optional.ofNullable(status);
        }

        private StatusMap(V value) {
            this.value = value;
        }

        public static <V> StatusMap of(V value, Enum status) {
            return new StatusMap(value, status);
        }

        public static <V> StatusMap of(V value) {
            return new StatusMap(value);
        }

        public V getValue() {
            return value;
        }

        public Optional<Enum> getStatus() {
            return status;
        }
    }

    public static class MappedStatus implements Status {
        private Enum status;

        public MappedStatus(Enum status) {
            this.status = status;
        }

        @Override
        public boolean isMapped() {
            return true;
        }

        public Enum get() {
            return status;
        }
    }

    public static class UnknownStatus implements Status {
        @Override
        public boolean isUnknown() {
            return true;
        }
    }

    public static class IgnoredStatus implements Status {
        @Override
        public boolean isIgnored() {
            return true;
        }
    }

    public static class Builder<V> {
        private List<StatusMap> validStatuses = new ArrayList<>();

        public Builder<V> map(V value, Enum status) {
            validStatuses.add(StatusMap.of(value, status));
            return this;
        }

        public Builder<V> ignore(V value) {
            validStatuses.add(StatusMap.of(value));
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
    public Status from(V value) {
        Optional<StatusMap<V>> statusMap = validStatuses
                .stream()
                .filter(p -> p.getValue().equals(value))
                .findFirst();

        if (!statusMap.isPresent()) {
            return new UnknownStatus();
        }

        Optional<Enum> status = statusMap.flatMap(StatusMap::getStatus);

        if (!status.isPresent()) {
            return new IgnoredStatus();
        }

        return new MappedStatus(status.get());
    }
}
