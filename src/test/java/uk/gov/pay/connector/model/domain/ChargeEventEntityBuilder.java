package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import java.time.ZonedDateTime;

public final class ChargeEventEntityBuilder {
    private Long id;
    private ChargeEntity chargeEntity;
    private ZonedDateTime updated;
    private ChargeStatus status;

    private ChargeEventEntityBuilder() {
    }

    public static ChargeEventEntityBuilder aChargeEventEntity() {
        return new ChargeEventEntityBuilder();
    }

    public ChargeEventEntityBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public ChargeEventEntityBuilder withStatus(ChargeStatus chargeStatus) {
        this.status = status;
        return this;
    }

    public ChargeEventEntityBuilder withChargeEntity(ChargeEntity chargeEntity) {
        this.chargeEntity = chargeEntity;
        return this;
    }

    public ChargeEventEntityBuilder withUpdated(ZonedDateTime updated) {
        this.updated = updated;
        return this;
    }

    public ChargeEventEntity build() {
        ChargeEventEntity chargeEventEntity = new ChargeEventEntity(chargeEntity, status, updated, null);
        chargeEventEntity.setId(id);
        return chargeEventEntity;
    }
}
