package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.jackson.JsonSnakeCase;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.time.ZonedDateTime;

@JsonSnakeCase
public class ChargeEvent {
    private String extChargeId;
    private ExternalChargeState state;
    private ZonedDateTime updated;

    public ChargeEvent(String extChargeId, ExternalChargeState state, ZonedDateTime updated) {
        this.extChargeId = extChargeId;
        this.state = state;
        this.updated = updated;
    }

    @JsonIgnore
    public String getChargeId() {
        return extChargeId;
    }

    public void setChargeId(String chargeId) {
        this.extChargeId = chargeId;
    }

    @JsonProperty("state")
    public ExternalChargeState getState() {
        return state;
    }

    @JsonProperty("updated")
    public String getUpdated() {
        return DateTimeUtils.toUTCDateString(updated);
    }

    @JsonIgnore
    public ZonedDateTime getTimeUpdate() {
        return updated;
    }

    @Override
    public String toString() {
        return "ChargeEvent{" +
                "chargeId=" + extChargeId +
                ", state=" + state +
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
        return state == that.state;
    }

    @Override
    public int hashCode() {
        int result = extChargeId.hashCode();
        result = 31 * result + state.hashCode();
        return result;
    }
}
