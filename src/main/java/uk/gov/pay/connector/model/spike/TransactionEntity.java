package uk.gov.pay.connector.model.spike;


import static uk.gov.pay.connector.model.spike.PaymentGatewayStateTransitionsNew.stateTransitionsFor;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.fromString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Version;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;
import uk.gov.pay.connector.model.domain.AbstractEntity;
import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.model.domain.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus;
import uk.gov.pay.connector.service.PaymentGatewayName;

@Entity
@Table(name = "transactions")
@SequenceGenerator(name = "transactions_transaction_id_seq", sequenceName = "transactions_transaction_id_seq", allocationSize = 1)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "operation")
@Access(AccessType.FIELD)
public abstract class TransactionEntity extends AbstractEntity {
    public enum TransactionOperation {
        REFUND, CHARGE;
    }

    //todo enum + converter instead of string
    @Column(name = "status")
    protected String status;

    @Embedded
    private Auth3dsDetailsEntity auth3dsDetails;

    @Embedded
    private CardDetailsEntity cardDetails;

    //todo enum + converter instead of string
    @Column(name = "operation")
    protected String operation;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    @OneToMany(mappedBy = "transactionEntity")
    @OrderBy("updated DESC")
    private List<TransactionEventEntity> events = new ArrayList<>();

//    // Maybe not a good idea? Could allow us to use this as a 'discriminator'
//    // in single table inheritance
//    private PaymentGatewayName paymentProvider;

    @JsonIgnore
    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "payment_request_id", updatable = false)
    private PaymentRequestEntity paymentRequest;

    public String getStatus() {
        return status;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public Long getAmount() {
        return amount;
    }

    public String getOperation() {
        return operation;
    }

    public PaymentRequestEntity getPaymentRequest() {
        return paymentRequest;
    }

    public CardDetailsEntity getCardDetails() {
        return cardDetails;
    }

    public Auth3dsDetailsEntity getAuth3dsDetails() {
        return auth3dsDetails;
    }

    public PaymentGatewayName getPaymentGatewayName() {
        return PaymentGatewayName.valueFrom(paymentRequest.getGatewayAccount().getGatewayName());
    }

    public void setStatus(TransactionStatus targetStatus) throws InvalidStateTransitionException {
        if (stateTransitionsFor(getPaymentGatewayName()).isValidTransition(fromString(this.status), targetStatus)) {
            this.status = targetStatus.getValue();
        } else {
            throw new InvalidStateTransitionException(this.status, targetStatus.getValue());
        }
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public List<TransactionEventEntity> getEvents() {
        return events;
    }

    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public void setPaymentRequest(PaymentRequestEntity paymentRequest) {
        this.paymentRequest = paymentRequest;
    }

    public void setAuth3dsDetails(Auth3dsDetailsEntity auth3dsDetails) {
        this.auth3dsDetails = auth3dsDetails;
    }

    public void setCardDetails(CardDetailsEntity cardDetails) {
        this.cardDetails = cardDetails;
    }

    public void setEvents(List<TransactionEventEntity> events) {
        this.events = events;
    }

    public TransactionEntity() {
        //for jpa
    }

}
