package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.domain.*;

import javax.persistence.*;

import static uk.gov.pay.connector.model.domain.PaymentGatewayStateTransitions.defaultTransitions;

@Entity
@DiscriminatorValue(value = "CHARGE")
public class ChargeTransactionEntity extends TransactionEntity<ChargeStatus> {

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    public ChargeTransactionEntity() {
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public void setStatus(ChargeStatus status) {
        if (this.status != null && !defaultTransitions().isValidTransition(this.status, status)) {
            throw new InvalidStateTransitionException(this.status.getValue(), status.getValue());
        }
        this.status = status;
    }

    public static ChargeTransactionEntity from(ChargeEntity chargeEntity) {
        ChargeTransactionEntity transactionEntity = new ChargeTransactionEntity();
        transactionEntity.setGatewayTransactionId(chargeEntity.getGatewayTransactionId());
        transactionEntity.setAmount(chargeEntity.getAmount());
        transactionEntity.setStatus(ChargeStatus.fromString(chargeEntity.getStatus()));
        transactionEntity.setOperation(TransactionOperation.CHARGE);

        return transactionEntity;
    }
}
