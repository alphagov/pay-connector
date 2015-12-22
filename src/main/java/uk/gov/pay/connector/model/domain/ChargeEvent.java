package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

import static uk.gov.pay.connector.model.domain.ChargeStatus.chargeStatusFrom;

public class ChargeEvent {

    private Long chargeId;
    private ChargeStatus status;
    private LocalDateTime updated;

    public ChargeEvent(Long chargeId, String status, LocalDateTime updated) {
        this(chargeId, chargeStatusFrom(status), updated);
    }

    public ChargeEvent(Long chargeId, ChargeStatus chargeStatus, LocalDateTime updated) {
        this.chargeId = chargeId;
        this.status = chargeStatus;
        this.updated = updated;
    }

    public ChargeEvent(Long chargeId, ChargeStatus chargeStatus) {
        this(chargeId, chargeStatus, LocalDateTime.now());
    }

    public ChargeStatus getStatus() {
        return status;
    }

    @JsonProperty("status")
    public String getStatusValue() {
        return status.getValue();
    }

    @JsonProperty
    public LocalDateTime getUpdated() {
        return updated;
    }

    @Override
    public String toString() {
        return "ChargeEvent{" +
                "status=" + status +
                ", updated=" + updated +
                '}';
    }

    public Long getChargeId() {
        return chargeId;
    }

    public static ChargeEvent from(Long chargeId, ChargeStatus chargeStatus) {
        return new ChargeEvent(chargeId, chargeStatus);
    }
}
