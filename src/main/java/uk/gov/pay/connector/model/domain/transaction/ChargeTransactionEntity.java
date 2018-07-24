package uk.gov.pay.connector.model.domain.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.PaymentGatewayStateTransitions;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

@Entity
@DiscriminatorValue(value = "CHARGE")
public class ChargeTransactionEntity extends TransactionEntity<ChargeStatus> {
    private static final Logger logger = LoggerFactory.getLogger(ChargeTransactionEntity.class);

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    protected ChargeStatus status;
    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    @OrderBy("updated DESC")
    @Column(name = "email")
    private String email;

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
        updateStatus(newStatus);
    }

    @Override
    public void updateStatus(ChargeStatus newStatus) {
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
        super.updateStatus(newStatus);
    }

    public static ChargeTransactionEntity from(ChargeEntity chargeEntity) {
        ChargeTransactionEntity transactionEntity = new ChargeTransactionEntity();
        transactionEntity.setGatewayTransactionId(chargeEntity.getGatewayTransactionId());
        transactionEntity.setAmount(chargeEntity.getAmount());
        transactionEntity.updateStatus(ChargeStatus.fromString(chargeEntity.getStatus()));
        transactionEntity.setCreatedDate(chargeEntity.getCreatedDate());

        return transactionEntity;
    }


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
