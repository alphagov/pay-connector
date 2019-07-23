package uk.gov.pay.connector.pact;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;

import java.time.ZonedDateTime;
import java.util.Optional;

import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

public class ChargeEventEntityFixture {
    private ChargeStatus chargeStatus = ChargeStatus.CAPTURED;
    private ZonedDateTime updated = ZonedDateTime.now();
    private ZonedDateTime gatewayEventDate;

    public static ChargeEventEntityFixture aValidChargeEventEntity() {
        return new ChargeEventEntityFixture();
    }

    public ChargeEventEntity build() {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withFee(42L)
                .build();

        return new ChargeEventEntity(chargeEntity, chargeStatus, updated, Optional.ofNullable(gatewayEventDate));
    }

    public ChargeEventEntityFixture withGatewayEventDate(ZonedDateTime value) {
        this.gatewayEventDate = value;
        return this;
    }
}
