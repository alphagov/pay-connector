package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class StatusMapper<V, S> {
    public static class StatusMap<V, S> {
        private V value;
        private Optional<S> status = Optional.empty();

        private StatusMap(V value, S status) {
            this.value = value;
            this.status = Optional.ofNullable(status);
        }

        private StatusMap(V value) {
            this.value = value;
        }

        public static <V, S> StatusMap of(V value, S status) {
            return new StatusMap(value, status);
        }

        public static <V> StatusMap of(V value) {
            return new StatusMap(value);
        }

        public V getValue() {
            return value;
        }

        public Optional<S> getStatus() {
            return status;
        }
    }

    public static abstract class Status<S> {

        public boolean isMapped() {
            return false;
        }

        public boolean isUnknown() {
            return false;
        }

        public boolean isIgnored() {
            return false;
        }

        public S get() {
            throw new NoSuchElementException("No status present");
        }
    }

    public static class MappedStatus<S> extends StatusMapper.Status<S> {
        private S status;

        public MappedStatus(S status) {
            this.status = status;
        }

        @Override
        public boolean isMapped() {
            return true;
        }

        public S get() {
            return status;
        }
    }

    public static class UnkonwnStatus<S> extends StatusMapper.Status<S> {
        @Override
        public boolean isUnknown() {
            return true;
        }
    }

    public static class IgnoredStatus<S> extends StatusMapper.Status<S> {
        @Override
        public boolean isIgnored() {
            return true;
        }
    }

    public static class Builder<V, S> {
        private List<StatusMap> validStatuses = new ArrayList<>();

        public Builder<V, S> map(V value, S status) {
            validStatuses.add(StatusMap.of(value, status));
            return this;
        }

        public Builder<V, S> ignore(V value) {
            validStatuses.add(StatusMap.of(value));
            return this;
        }

        public StatusMapper<V, S> build() {
            return new StatusMapper(ImmutableList.copyOf(validStatuses));
        }
    }

    private final List<StatusMap<V, S>> validStatuses;

    private StatusMapper(List<StatusMap<V, S>> validStatuses) {
        this.validStatuses = validStatuses;
    }

    public static <V, T> Builder<V, T> builder() {
        return new Builder();
    }

    public StatusMapper.Status<S> from(V value) {
        Optional<StatusMap<V, S>> statusMap = validStatuses
                .stream()
                .filter(p -> p.getValue().equals(value))
                .findFirst();

        if (!statusMap.isPresent()) {
            return new UnkonwnStatus();
        }

        Optional<S> status = statusMap.flatMap(StatusMap::getStatus);

        if (!status.isPresent()) {
            return new IgnoredStatus<>();
        }

        return new MappedStatus(status.get());
    }

}