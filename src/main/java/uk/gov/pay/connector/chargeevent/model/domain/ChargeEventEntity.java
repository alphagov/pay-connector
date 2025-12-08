package uk.gov.pay.connector.chargeevent.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;

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
    @Schema(example = "CAPTURED")
    private ChargeStatus status;

    @Column(name = "gateway_event_date")
    @Convert(converter = UTCDateTimeConverter.class)
    @Schema(example = "2022-05-27T09:17:19.162Z")
    private ZonedDateTime gatewayEventDate;

    @Column(insertable = false, updatable = false)
    @Convert(converter = LocalDateTimeConverter.class)
    @Schema(example = "1656606727.366582000")
    private ZonedDateTime updated;

    protected ChargeEventEntity() {
    }

    private ChargeEventEntity(ChargeEntity chargeEntity, ChargeStatus chargeStatus, ZonedDateTime updated, ZonedDateTime gatewayEventDate) {
        this.chargeEntity = chargeEntity;
        this.status = chargeStatus;
        this.gatewayEventDate = gatewayEventDate;
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

    public static final class ChargeEventEntityBuilder {
        private ChargeEntity chargeEntity;
        private ChargeStatus status;
        private ZonedDateTime gatewayEventDate;
        private ZonedDateTime updated;

        private ChargeEventEntityBuilder() {
        }

        public static ChargeEventEntityBuilder aChargeEventEntity() {
            return new ChargeEventEntityBuilder();
        }

        public ChargeEventEntityBuilder withChargeEntity(ChargeEntity chargeEntity) {
            this.chargeEntity = chargeEntity;
            return this;
        }

        public ChargeEventEntityBuilder withStatus(ChargeStatus status) {
            this.status = status;
            return this;
        }

        public ChargeEventEntityBuilder withGatewayEventDate(ZonedDateTime gatewayEventDate) {
            this.gatewayEventDate = gatewayEventDate;
            return this;
        }

        public ChargeEventEntityBuilder withUpdated(ZonedDateTime updated) {
            this.updated = updated;
            return this;
        }

        public ChargeEventEntity build() {
            return new ChargeEventEntity(chargeEntity, status, updated, gatewayEventDate);
        }
    }
}
