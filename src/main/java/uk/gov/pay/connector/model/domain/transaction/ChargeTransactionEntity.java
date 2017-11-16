package uk.gov.pay.connector.model.domain.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.*;

import javax.persistence.*;

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

    public void setStatus(ChargeStatus status) {
        if (this.status != null && !defaultTransitions().isValidTransition(this.status, status)) {
            logger.warn(
                    format("Charge state transition [%s] -> [%s] not allowed for externalId [%s] transactionId [%s]",
                            this.status.getValue(),
                            status.getValue(),
                            (getPaymentRequest() != null) ? getPaymentRequest().getExternalId() : "not set",
                            getId()
                    )
            );
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
