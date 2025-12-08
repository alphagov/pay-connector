package uk.gov.pay.connector.pact;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.fee.model.Fee;

import java.time.ZonedDateTime;

import static java.util.concurrent.ThreadLocalRandom.current;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity.ChargeEventEntityBuilder.aChargeEventEntity;

public class ChargeEventEntityFixture {
    private ChargeStatus chargeStatus = ChargeStatus.CAPTURED;
    private ZonedDateTime updated = ZonedDateTime.now();
    private ZonedDateTime gatewayEventDate;
    private Long id = current().nextLong(0, Long.MAX_VALUE);
    private ChargeEntity charge = aValidChargeEntity()
            .withFee(Fee.of(null, 42L))
            .build();

    public static ChargeEventEntityFixture aValidChargeEventEntity() {
        return new ChargeEventEntityFixture();
    }

    public ChargeEventEntity build() {
        ChargeEventEntity chargeEventEntity = aChargeEventEntity()
                .withChargeEntity(charge)
                .withStatus(chargeStatus)
                .withUpdated(updated)
                .withGatewayEventDate(gatewayEventDate)
                .build();
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

    public ChargeEventEntityFixture withTimestamp(ZonedDateTime updated) {
        this.updated = updated;
        return this;
    }

    public ChargeEventEntityFixture withChargeStatus(ChargeStatus chargeStatus) {
        this.chargeStatus = chargeStatus;
        return this;
    }
}
