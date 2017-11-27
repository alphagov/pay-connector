package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.model.domain.RefundStatus;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity
@DiscriminatorValue(value = "REFUND")
public class RefundTransactionEventEntity extends TransactionEventEntity<RefundStatus, RefundTransactionEventEntity> {
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private RefundStatus status;

    @Override
    public RefundStatus getStatus() {
        return status;
    }

    @Override
    public void setStatus(RefundStatus status) {
        this.status = status;
    }
}
