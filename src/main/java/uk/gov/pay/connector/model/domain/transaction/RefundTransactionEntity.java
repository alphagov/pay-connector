package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundStatus;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue(value = "REFUND")
public class RefundTransactionEntity extends TransactionEntity<RefundStatus> {
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    protected RefundStatus status;
    @Column(name = "refund_external_id")
    private String refundExternalId;
    @Column(name = "user_external_id")
    private String userExternalId;
    @Column(name = "refund_reference")
    private String refundReference;

    public RefundTransactionEntity() {
        super(TransactionOperation.REFUND);
    }

    @Override
    public RefundStatus getStatus() {
        return status;
    }

    @Override
    void setStatus(RefundStatus status) {
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

}
