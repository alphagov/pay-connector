package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import java.time.ZonedDateTime;

@Entity
@DiscriminatorValue(value = "CHARGE")
public class ChargeTransactionEventEntity extends TransactionEventEntity<ChargeStatus, ChargeTransactionEventEntity> {
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ChargeStatus status;

    @Column(name = "gateway_event_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime gatewayEventDate;

    @Override
    public ChargeStatus getStatus() {
        return status;
    }

    @Override
    public void setStatus(ChargeStatus status) {
        this.status = status;
    }

    public ZonedDateTime getGatewayEventDate() {
        return gatewayEventDate;
    }

    public void setGatewayEventDate(ZonedDateTime gatewayEventDate) {
        this.gatewayEventDate = gatewayEventDate;
    }
}
