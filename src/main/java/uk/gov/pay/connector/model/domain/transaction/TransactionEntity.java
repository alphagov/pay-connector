package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.domain.*;

import javax.persistence.*;

import static uk.gov.pay.connector.model.domain.PaymentGatewayStateTransitions.defaultTransitions;

@Entity
@Table(name = "transactions")
public class TransactionEntity extends AbstractEntity {

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "refund_external_id")
    private String refundExternalId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private ChargeStatus status;

    @Column(name = "user_external_id")
    private String userExternalId;

    @Column(name = "operation")
    @Enumerated(EnumType.STRING)
    private TransactionOperation operation;

    @ManyToOne
    @JoinColumn(name = "payment_request_id", referencedColumnName = "id", updatable = false)
    private PaymentRequestEntity paymentRequest;

    public TransactionEntity() {
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getRefundExternalId() {
        return refundExternalId;
    }

    public void setRefundExternalId(String refundExternalId) {
        this.refundExternalId = refundExternalId;
    }

    public ChargeStatus getStatus() {
        return status;
    }

    public void setStatus(ChargeStatus status) {
        checkValidStateTransition(status);
        this.status = status;
    }

    @Deprecated // should be removed with ChargeEntity
    public void setChargeStatus(String status) {
        this.status = ChargeStatus.fromString(status);
    }

    public String getUserExternalId() {
        return userExternalId;
    }

    public void setUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
    }

    public PaymentRequestEntity getPaymentRequest() {
        return paymentRequest;
    }

    public void setPaymentRequest(PaymentRequestEntity paymentRequest) {
        this.paymentRequest = paymentRequest;
    }

    public TransactionOperation getOperation() {
        return operation;
    }

    public void setOperation(TransactionOperation operation) {
        this.operation = operation;
    }

    public static TransactionEntity from(ChargeEntity chargeEntity) {
        TransactionEntity transactionEntity = new TransactionEntity();
        transactionEntity.setGatewayTransactionId(chargeEntity.getGatewayTransactionId());
        transactionEntity.setAmount(chargeEntity.getAmount());
        transactionEntity.setStatus(ChargeStatus.fromString(chargeEntity.getStatus()));
        transactionEntity.setOperation(TransactionOperation.CHARGE);

        return transactionEntity;
    }

    private void checkValidStateTransition(ChargeStatus newChargeStatus) {
        if (status != null && !defaultTransitions().isValidTransition(status, newChargeStatus)) {
            throw new InvalidStateTransitionException(status.getValue(), newChargeStatus.getValue());
        }
    }
}
