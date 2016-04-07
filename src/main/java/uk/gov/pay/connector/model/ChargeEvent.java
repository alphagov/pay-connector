package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.jackson.JsonSnakeCase;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZonedDateTime;

@JsonSnakeCase
public class ChargeEvent {

    private Long chargeId;
    private ExternalChargeStatus status;

    private ZonedDateTime updated;

    public ChargeEvent(Long chargeId, ExternalChargeStatus chargeStatus, ZonedDateTime updated) {
        this.chargeId = chargeId;
        this.status = chargeStatus;
        this.updated = updated;
    }

    public Long getChargeId() {
        return chargeId;
    }

    public void setChargeId(Long chargeId) {
        this.chargeId = chargeId;
    }

    public ExternalChargeStatus getStatus() {
        return status;
    }

    public void setStatus(ExternalChargeStatus status) {
        this.status = status;
    }

    @JsonProperty("status")
    public String getExternalStatusString() {
        return status.getValue();
    }

    @JsonProperty("updated")
    public String getUpdated() {
        return DateTimeUtils.toUTCDateString(updated);
    }

    @Override
    public String toString() {
        return "ChargeEvent{" +
                "chargeId=" + chargeId +
                ", status=" + status +
                ", updated=" + updated +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        // same charge with same status is treated equal
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChargeEvent that = (ChargeEvent) o;

        if (!chargeId.equals(that.chargeId)) return false;
        return status == that.status;
    }

    @Override
    public int hashCode() {
        int result = chargeId.hashCode();
        result = 31 * result + status.hashCode();
        return result;
    }
}
