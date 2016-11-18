package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "charge_events")
@SequenceGenerator(name = "charge_events_charge_id_seq", sequenceName = "charge_events_charge_id_seq", allocationSize = 1)
public class ChargeEventEntity extends AbstractEntity {

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "charge_id", updatable = false)
    private ChargeEntity chargeEntity;

    @Convert(converter = ChargeStatusConverter.class)
    private ChargeStatus status;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransctionId;

    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime updated;

    protected ChargeEventEntity() {
    }

    public ChargeEventEntity(ChargeEntity chargeEntity, ChargeStatus chargeStatus, ZonedDateTime updated) {
        this.chargeEntity = chargeEntity;
        this.status = chargeStatus;
        this.updated = updated;
        this.gatewayTransctionId = chargeEntity.getGatewayTransactionId();
    }

    public ChargeStatus getStatus() {
        return status;
    }

    public ZonedDateTime getUpdated() {
        return updated;
    }

    public ChargeEntity getChargeEntity() {
        return chargeEntity;
    }

    public String getGatewayTransctionId() {
        return gatewayTransctionId;
    }

    public static ChargeEventEntity from(ChargeEntity chargeEntity, ChargeStatus chargeStatus, ZonedDateTime updated) {
        return new ChargeEventEntity(chargeEntity, chargeStatus, updated);
    }
}
