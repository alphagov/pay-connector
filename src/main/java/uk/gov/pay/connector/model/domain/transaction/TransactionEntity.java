package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.model.domain.AbstractEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.Status;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "operation")
public abstract class TransactionEntity<E extends Status> extends AbstractEntity {
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    protected E status;
    @Column(name = "amount")
    private Long amount;
    @Column(name = "operation")
    @Enumerated(EnumType.STRING)
    private TransactionOperation operation;
    @ManyToOne
    @JoinColumn(name = "payment_request_id", referencedColumnName = "id", updatable = false)
    private PaymentRequestEntity paymentRequest;
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    private List<TransactionEventEntity> transactionEvents = new ArrayList<>();

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
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

    public E getStatus() {
        return status;
    }

    public List<TransactionEventEntity> getTransactionEvents() {
        return transactionEvents;
    }

    protected void addTransactionEvent(TransactionEventEntity<E> transactionEvent, E newStatus) {
        transactionEvent.setTransaction(this);
        transactionEvent.setStatus(newStatus);
        transactionEvent.setUpdated(ZonedDateTime.now());
        getTransactionEvents().add(transactionEvent);
    }

    public abstract void updateStatus(E newStatus);
    public abstract void updateStatus(E newStatus, ZonedDateTime gatewayEventTime);
}
