package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity
@DiscriminatorValue(value = "CHARGE")
public class ChargeTransactionEventEntity extends TransactionEventEntity<ChargeStatus> {
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ChargeStatus status;

    @Override
    public ChargeStatus getStatus() {
        return status;
    }

    @Override
    public void setStatus(ChargeStatus status) {
        this.status = status;
    }
}
