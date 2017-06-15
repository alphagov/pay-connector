package uk.gov.pay.connector.model;

import java.util.Objects;

public class ProviderStatusOnly<T> implements StatusMapFromStatus<T> {

    private final T status;

    public static <T> ProviderStatusOnly of(T status) {
        return new ProviderStatusOnly<>(status);
    }

    private ProviderStatusOnly(T status) {
        this.status = Objects.requireNonNull(status);
    }

    @Override
    public T getProviderStatus() {
        return status;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof StatusMapFromStatus<?>)) {
            return false;
        }

        StatusMapFromStatus<?> that = (StatusMapFromStatus<?>) other;
        return this.getProviderStatus().equals(that.getProviderStatus());
    }

    @Override
    public int hashCode() {
        return 31 * status.hashCode();
    }

    @Override
    public String toString() {
        return "ProviderStatusOnly{status=" + status + '}';
    }

}
