package uk.gov.pay.connector.chargeevents.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.ZonedDateTime;
import java.util.Optional;

@Entity
@Table(name = "charge_events")
@SequenceGenerator(name = "charge_events_id_seq",
        sequenceName = "charge_events_id_seq", allocationSize = 1)
public class ChargeEventEntity extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "charge_events_id_seq")
    @JsonIgnore
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "charge_id", updatable = false)
    private ChargeEntity chargeEntity;

    @Convert(converter = ChargeStatusConverter.class)
    private ChargeStatus status;

    @Column(name = "gateway_event_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime gatewayEventDate;

    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime updated;

    protected ChargeEventEntity() {
    }

    public ChargeEventEntity(ChargeEntity chargeEntity, ChargeStatus chargeStatus, ZonedDateTime updated, Optional<ZonedDateTime> gatewayEventDate) {
        this.chargeEntity = chargeEntity;
        this.status = chargeStatus;
        this.gatewayEventDate = gatewayEventDate.orElse(null);
        this.updated = updated;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ChargeStatus getStatus() {
        return status;
    }

    public Optional<ZonedDateTime> getGatewayEventDate() {
        return Optional.ofNullable(gatewayEventDate);
    }

    public ZonedDateTime getUpdated() {
        return updated;
    }

    public ChargeEntity getChargeEntity() {
        return chargeEntity;
    }

    public static ChargeEventEntity from(ChargeEntity chargeEntity, ChargeStatus chargeStatus, ZonedDateTime updated, Optional<ZonedDateTime> gatewayEventDate) {
        return new ChargeEventEntity(chargeEntity, chargeStatus, updated, gatewayEventDate);
    }
}
