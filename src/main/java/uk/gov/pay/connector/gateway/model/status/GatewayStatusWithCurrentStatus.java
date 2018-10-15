package uk.gov.pay.connector.gateway.model.status;

import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.Status;

import java.util.Objects;

public class GatewayStatusWithCurrentStatus<T> implements StatusMapFromStatus<T> {

    private final T status;
    private final Status currentStatus;

    public static <T> GatewayStatusWithCurrentStatus of(T status, ChargeStatus currentStatus) {
        return new GatewayStatusWithCurrentStatus<>(status, currentStatus);
    }

    private GatewayStatusWithCurrentStatus(T status, ChargeStatus currentStatus) {
        this.status = Objects.requireNonNull(status);
        this.currentStatus = Objects.requireNonNull(currentStatus);
    }

    @Override
    public T getGatewayStatus() {
        return status;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof GatewayStatusWithCurrentStatus<?>) {
            GatewayStatusWithCurrentStatus<?> that = (GatewayStatusWithCurrentStatus<?>) other;
            return this.getGatewayStatus().equals(that.getGatewayStatus()) && this.currentStatus.equals(that.currentStatus);
        }

        if (other instanceof GatewayStatusOnly<?>) {
            GatewayStatusOnly<?> that = (GatewayStatusOnly<?>) other;
            return this.status.equals(that.getGatewayStatus());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return 31 * status.hashCode();
    }

    @Override
    public String toString() {
        return "GatewayStatusWithCurrentStatus{notificationStatus=" + status + ", currentStatus=" + currentStatus + '}';
    }

}
