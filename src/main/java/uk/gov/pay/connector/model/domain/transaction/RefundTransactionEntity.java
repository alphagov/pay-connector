package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue(value = "REFUND")
public class RefundTransactionEntity extends TransactionEntity<RefundStatus, RefundTransactionEventEntity> {
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    protected RefundStatus status;
    @Column(name = "refund_external_id")
    private String refundExternalId;
    @Column(name = "user_external_id")
    private String userExternalId;
    @Column(name = "refund_reference")
    private String refundReference;
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    @OrderBy("updated DESC")
    private List<RefundTransactionEventEntity> transactionEvents = new ArrayList<>();
    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    public RefundTransactionEntity() {
        super(TransactionOperation.REFUND);
    }

    @Override
    public RefundStatus getStatus() {
        return status;
    }

    @Override
    public void setStatus(RefundStatus status) {
        this.status = status;
    }

    public String getRefundExternalId() {
        return refundExternalId;
    }

    public void setRefundExternalId(String refundExternalId) {
        this.refundExternalId = refundExternalId;
    }

    public String getUserExternalId() {
        return userExternalId;
    }

    public void setUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
    }

    public String getRefundReference() {
        return refundReference;
    }

    public void setRefundReference(String refundReference) {
        this.refundReference = refundReference;
    }

    public static RefundTransactionEntity from(RefundEntity refundEntity) {
        RefundTransactionEntity refundTransaction = new RefundTransactionEntity();
        refundTransaction.setAmount(refundEntity.getAmount());
        refundTransaction.setRefundExternalId(refundEntity.getExternalId());
        refundTransaction.setUserExternalId(refundEntity.getUserExternalId());
        refundTransaction.updateStatus(refundEntity.getStatus());
        refundTransaction.setCreatedDate(refundEntity.getCreatedDate());

        return refundTransaction;
    }

    @Override
    protected RefundTransactionEventEntity createNewTransactionEvent() {
        return new RefundTransactionEventEntity();
    }

    public List<RefundTransactionEventEntity> getTransactionEvents() {
        return transactionEvents;
    }

    @Override
    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }
    @Override
    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }
}
