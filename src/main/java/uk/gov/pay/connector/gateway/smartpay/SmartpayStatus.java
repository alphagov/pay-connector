package uk.gov.pay.connector.gateway.smartpay;

import java.util.Objects;

public class SmartpayStatus {

    private final String eventCode;
    private final boolean success;

    public SmartpayStatus(String eventCode, boolean success) {
        this.eventCode = eventCode;
        this.success = success;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SmartpayStatus that = (SmartpayStatus) o;
        return success == that.success &&
                Objects.equals(eventCode, that.eventCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventCode, success);
    }
}
