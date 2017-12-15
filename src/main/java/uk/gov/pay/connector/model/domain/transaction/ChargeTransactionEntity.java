package uk.gov.pay.connector.model.domain.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.PaymentGatewayStateTransitions;

import javax.persistence.*;
import java.time.ZonedDateTime;

import static java.lang.String.format;

@Entity
@DiscriminatorValue(value = "CHARGE")
public class ChargeTransactionEntity extends TransactionEntity<ChargeStatus, ChargeTransactionEventEntity> {
    private static final Logger logger = LoggerFactory.getLogger(ChargeTransactionEntity.class);

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    protected ChargeStatus status;
    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    public ChargeTransactionEntity() {
        super(TransactionOperation.CHARGE);
    }

    @Override
    public ChargeStatus getStatus() {
        return status;
    }

    @Override
    void setStatus(ChargeStatus status) {
        this.status = status;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public void updateStatus(ChargeStatus newStatus, ZonedDateTime gatewayEventTime) {
        ChargeTransactionEventEntity transactionEvent = createNewTransactionEvent();
        transactionEvent.setGatewayEventDate(gatewayEventTime);
        updateStatus(newStatus, transactionEvent);
    }

    @Override
    protected ChargeTransactionEventEntity createNewTransactionEvent() {
        return new ChargeTransactionEventEntity();
    }

    void updateStatus(ChargeStatus newStatus, ChargeTransactionEventEntity transactionEvent) {
        if (this.status != null && !PaymentGatewayStateTransitions.getInstance().isValidTransition(this.status, newStatus)) {
            logger.warn(
                    format("Charge state transition [%s] -> [%s] not allowed for externalId [%s] transactionId [%s]",
                            this.status.getValue(),
                            newStatus.getValue(),
                            (getPaymentRequest() != null) ? getPaymentRequest().getExternalId() : "not set",
                            getId()
                    )
            );
        }
        super.updateStatus(newStatus, transactionEvent);
    }

    public static ChargeTransactionEntity from(ChargeEntity chargeEntity) {
        ChargeTransactionEntity transactionEntity = new ChargeTransactionEntity();
        transactionEntity.setGatewayTransactionId(chargeEntity.getGatewayTransactionId());
        transactionEntity.setAmount(chargeEntity.getAmount());
        transactionEntity.updateStatus(ChargeStatus.fromString(chargeEntity.getStatus()));

        return transactionEntity;
    }
}
