package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.joda.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.dropwizard.jackson.JsonSnakeCase;

import javax.persistence.*;
import java.time.LocalDateTime;

import static uk.gov.pay.connector.model.api.ExternalChargeStatus.mapFromStatus;
import static uk.gov.pay.connector.model.domain.ChargeStatus.chargeStatusFrom;

@Entity
@JsonSnakeCase
@Table(name = "charge_events")
@Embeddable
public class ChargeEventEntity extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "charge_id", updatable = false)
    private ChargeEntity chargeEntity;

    @Convert(converter = ChargeStatusConverter.class)
    private ChargeStatus status;

    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime updated;

    protected ChargeEventEntity() {}

    public ChargeEventEntity(ChargeEntity chargeEntity, String status, LocalDateTime updated) {
        this(chargeEntity, chargeStatusFrom(status), updated);
    }

    public ChargeEventEntity(ChargeEntity chargeEntity, ChargeStatus chargeStatus, LocalDateTime updated) {
        this.chargeEntity = chargeEntity;
        this.status = chargeStatus;
        this.updated = updated;
    }

    public ChargeStatus getStatus() {
        return status;
    }

    @JsonProperty("status")
    public String getExternalStatusValue() {
        return mapFromStatus(status).getValue();
    }

    @JsonProperty
    public LocalDateTime getUpdated() {
        return updated;
    }

    public ChargeEntity getChargeEntity() {
        return chargeEntity;
    }

    public static ChargeEventEntity from(ChargeEntity chargeEntity, ChargeStatus chargeStatus, LocalDateTime updated) {
        return new ChargeEventEntity(chargeEntity, chargeStatus, updated);
    }
}
