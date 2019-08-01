package uk.gov.pay.connector.pact;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;
import java.util.Optional;

import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

public class ChargeEventEntityFixture {
    private Long chargeId;
    private ChargeStatus chargeStatus = ChargeStatus.CAPTURED;
    private ZonedDateTime updated = ZonedDateTime.now();
    private ZonedDateTime gatewayEventDate;
    private Long id;
    private ChargeEntity charge;

    public static ChargeEventEntityFixture aValidChargeEventEntity() {
        return new ChargeEventEntityFixture();
    }

    public ChargeEventEntity build() {
        if (this.charge == null) {
            ChargeEntityFixture chargeEntityFixture = aValidChargeEntity()
                    .withFee(42L);

            if (this.chargeId != null) {
                chargeEntityFixture.withId(this.chargeId);
            }

            this.charge = chargeEntityFixture.build();
        }

        ChargeEventEntity chargeEventEntity = new ChargeEventEntity(this.charge, chargeStatus, updated, Optional.ofNullable(gatewayEventDate));
        chargeEventEntity.setId(id);

        return chargeEventEntity;
    }

    public ChargeEventEntityFixture withGatewayEventDate(ZonedDateTime value) {
        this.gatewayEventDate = value;
        return this;
    }

    public ChargeEventEntityFixture withId(Long id) {
        this.id = id;
        return this;
    }

    public ChargeEventEntityFixture withCharge(ChargeEntity charge) {
        this.charge = charge;
        return this;
    }

    public ChargeEventEntityFixture withChargeId(Long value) {
        this.chargeId = value;
        return this;
    }

    public ChargeEventEntityFixture withStatus(ChargeStatus value) {
        this.chargeStatus = value;
        return this;
    }
}
