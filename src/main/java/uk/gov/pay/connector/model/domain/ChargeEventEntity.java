package uk.gov.pay.connector.model.domain;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "charge_events")
@SequenceGenerator(name = "charge_events_charge_id_seq", sequenceName = "charge_events_charge_id_seq", allocationSize = 1)
public class ChargeEventEntity extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "charge_id", updatable = false)
    private ChargeEntity chargeEntity;

    @Convert(converter = ChargeStatusConverter.class)
    private ChargeStatus status;

    @Convert(converter = LocalDateTimeConverter.class)
    private LocalDateTime updated;

    protected ChargeEventEntity() {
    }

    public ChargeEventEntity(ChargeEntity chargeEntity, ChargeStatus chargeStatus, LocalDateTime updated) {
        this.chargeEntity = chargeEntity;
        this.status = chargeStatus;
        this.updated = updated;
    }

    public ChargeStatus getStatus() {
        return status;
    }

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
