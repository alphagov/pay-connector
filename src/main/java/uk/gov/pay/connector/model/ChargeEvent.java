package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.jackson.JsonSnakeCase;
import uk.gov.pay.connector.model.api.LegacyChargeStatus;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZonedDateTime;

@JsonSnakeCase
public class ChargeEvent {

    private String extChargeId;
    private LegacyChargeStatus status;

    private ZonedDateTime updated;

    public ChargeEvent(String extChargeId, LegacyChargeStatus chargeStatus, ZonedDateTime updated) {
        this.extChargeId = extChargeId;
        this.status = chargeStatus;
        this.updated = updated;
    }

    @JsonIgnore
    public String getChargeId() {
        return extChargeId;
    }

    public void setChargeId(String chargeId) {
        this.extChargeId = chargeId;
    }

    public LegacyChargeStatus getStatus() {
        return status;
    }

    public void setStatus(LegacyChargeStatus status) {
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
                "chargeId=" + extChargeId +
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

        if (!extChargeId.equals(that.extChargeId)) return false;
        return status == that.status;
    }

    @Override
    public int hashCode() {
        int result = extChargeId.hashCode();
        result = 31 * result + status.hashCode();
        return result;
    }
}
