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
    private Long id;
    private ChargeEntity charge = aValidChargeEntity()
            .withFee(42L)
            .build();

    public static ChargeEventEntityFixture aValidChargeEventEntity() {
        return new ChargeEventEntityFixture();
    }

    public ChargeEventEntity build() {

        ChargeEventEntity chargeEventEntity = new ChargeEventEntity(charge, chargeStatus, updated, Optional.ofNullable(gatewayEventDate));
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
    
    
    
    
}
