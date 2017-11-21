package uk.gov.pay.connector.model.domain.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.*;

import javax.persistence.*;

import java.time.ZonedDateTime;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.PaymentGatewayStateTransitions.defaultTransitions;

@Entity
@DiscriminatorValue(value = "CHARGE")
public class ChargeTransactionEntity extends TransactionEntity<ChargeStatus> {
    private static final Logger logger = LoggerFactory.getLogger(ChargeTransactionEntity.class);

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

    public void updateStatus(ChargeStatus newStatus, ZonedDateTime gatewayEventTime) {
        ChargeTransactionEventEntity transactionEvent = new ChargeTransactionEventEntity();
        transactionEvent.setGatewayEventDate(gatewayEventTime);
        updateStatus(newStatus, transactionEvent);
    }

    public void updateStatus(ChargeStatus newStatus) {
        updateStatus(newStatus, new ChargeTransactionEventEntity());
    }

    private void updateStatus(ChargeStatus newStatus, ChargeTransactionEventEntity transactionEvent) {
        if (this.status != null && !defaultTransitions().isValidTransition(this.status, newStatus)) {
            logger.warn(
                    format("Charge state transition [%s] -> [%s] not allowed for externalId [%s] transactionId [%s]",
                            this.status.getValue(),
                            newStatus.getValue(),
                            (getPaymentRequest() != null) ? getPaymentRequest().getExternalId() : "not set",
                            getId()
                    )
            );
        }
        this.status = newStatus;
        addTransactionEvent(transactionEvent, newStatus);
    }

    public static ChargeTransactionEntity from(ChargeEntity chargeEntity) {
        ChargeTransactionEntity transactionEntity = new ChargeTransactionEntity();
        transactionEntity.setGatewayTransactionId(chargeEntity.getGatewayTransactionId());
        transactionEntity.setAmount(chargeEntity.getAmount());
        transactionEntity.updateStatus(ChargeStatus.fromString(chargeEntity.getStatus()));
        transactionEntity.setOperation(TransactionOperation.CHARGE);

        return transactionEntity;
    }
}
