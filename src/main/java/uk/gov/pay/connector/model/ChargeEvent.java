package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.dropwizard.jackson.JsonSnakeCase;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;

import java.time.LocalDateTime;

@JsonSnakeCase
public class ChargeEvent {

    private Long chargeId;
    private ExternalChargeStatus status;

    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updated;

    public ChargeEvent(Long chargeId, ExternalChargeStatus chargeStatus, LocalDateTime updated) {
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

    public LocalDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(LocalDateTime updated) {
        this.updated = updated;
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
