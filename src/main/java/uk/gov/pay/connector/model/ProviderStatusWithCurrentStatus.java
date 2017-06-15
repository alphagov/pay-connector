package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.Status;

import java.util.Objects;

public class ProviderStatusWithCurrentStatus<T> implements StatusMapFromStatus<T> {

    private final T status;
    private final Status currentStatus;

    public static <T> ProviderStatusWithCurrentStatus of(T status, ChargeStatus currentStatus) {
        return new ProviderStatusWithCurrentStatus<>(status, currentStatus);
    }

    private ProviderStatusWithCurrentStatus(T status, ChargeStatus currentStatus) {
        this.status = Objects.requireNonNull(status);
        this.currentStatus = Objects.requireNonNull(currentStatus);
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

        if (other instanceof ProviderStatusWithCurrentStatus<?>) {
            ProviderStatusWithCurrentStatus<?> that = (ProviderStatusWithCurrentStatus<?>) other;
            return this.getProviderStatus().equals(that.getProviderStatus()) && this.currentStatus.equals(that.currentStatus);
        }

        if (other instanceof ProviderStatusOnly<?>) {
            ProviderStatusOnly<?> that = (ProviderStatusOnly<?>) other;
            return this.status.equals(that.getProviderStatus());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return 31 * status.hashCode();
    }

    @Override
    public String toString() {
        return "ProviderStatusWithCurrentStatus{notificationStatus=" + status + ", currentStatus=" + currentStatus + '}';
    }

}
