package uk.gov.pay.connector.model.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import uk.gov.pay.connector.model.domain.Status;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.ZonedDateTime;
import java.util.List;


@Entity
@Table(name = "transactions")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "operation")
@SequenceGenerator(name = "transactions_id_seq",
        sequenceName = "transactions_id_seq",
        allocationSize = 1)
public abstract class TransactionEntity<S extends Status> extends AbstractVersionedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transactions_id_seq")
    @JsonIgnore
    private Long id;
    @Column(name = "amount")
    private Long amount;
    @Column(name = "operation")
    @Enumerated(EnumType.STRING)
    private TransactionOperation operation;
    @ManyToOne
    @JoinColumn(name = "payment_request_id", referencedColumnName = "id", updatable = false)
    private PaymentRequestEntity paymentRequest;
    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;
    @Column(name = "gateway_account_id")
    // This just needs to be set when we save the transaction. It is then used to optimise
    // transaction search don't access in Java code.
    private Long gatewayAccountId;
    @Column(name = "user_external_id")
    // This is the user external id of whoever issued a refund.
    private String userExternalId;

    TransactionEntity(TransactionOperation operation) {
        this.operation = operation;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
        this.gatewayAccountId = paymentRequest.getGatewayAccount().getId();
    }

    //todo remove this once we have back filled the gatewayAccountId.
    public void setGatewayAccountId(Long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
    }

    public TransactionOperation getOperation() {
        return operation;
    }

    public abstract S getStatus();
    abstract void setStatus(S status);

    public void updateStatus(S newStatus) {
        setStatus(newStatus);
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

}
