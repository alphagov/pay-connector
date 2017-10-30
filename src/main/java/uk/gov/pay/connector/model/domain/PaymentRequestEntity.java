package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionEntity;
import uk.gov.pay.connector.model.domain.transaction.TransactionOperation;

import javax.persistence.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

@Entity
@Table(name = "payment_requests")
@Access(AccessType.FIELD)
public class PaymentRequestEntity extends AbstractEntity {

    @Column(name = "amount")
    private Long amount;

    @Column(name = "return_url")
    private String returnUrl;

    @ManyToOne
    @JoinColumn(name = "gateway_account_id", updatable = false)
    private GatewayAccountEntity gatewayAccount;

    @Column(name = "description")
    private String description;

    @Column(name = "reference")
    private String reference;

    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    @Column(name = "external_id")
    private String externalId;

    @OneToMany(mappedBy = "paymentRequest", cascade = CascadeType.ALL)
    private List<TransactionEntity> transactions = new ArrayList<>();

    public PaymentRequestEntity() {
        // enjoy it JPA
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public GatewayAccountEntity getGatewayAccount() {
        return gatewayAccount;
    }

    public void setGatewayAccount(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccount = gatewayAccount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public List<TransactionEntity> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    public void addTransaction(TransactionEntity transactionEntity) {
        this.transactions.add(transactionEntity);
        transactionEntity.setPaymentRequest(this);
    }

    public void setTransactions(List<TransactionEntity> transactions) {
        this.transactions = transactions;
        transactions.forEach(transactionEntity -> transactionEntity.setPaymentRequest(this));
    }

    public ChargeTransactionEntity getChargeTransaction() {
        return (ChargeTransactionEntity)transactions.stream().filter(byChargeTransactions()).findFirst().orElseThrow(
                () -> new IllegalStateException("Payment Request has been initialised without a charge transaction")
        );
    }

    private Predicate<TransactionEntity> byChargeTransactions() {
        return transactionEntity -> transactionEntity.getOperation().equals(TransactionOperation.CHARGE);
    }

    public static PaymentRequestEntity from(ChargeEntity chargeEntity, ChargeTransactionEntity transactionEntity) {
        PaymentRequestEntity paymentEntity = new PaymentRequestEntity();
        paymentEntity.setAmount(chargeEntity.getAmount());
        paymentEntity.setCreatedDate(chargeEntity.getCreatedDate());
        paymentEntity.setDescription(chargeEntity.getDescription());
        paymentEntity.setExternalId(chargeEntity.getExternalId());
        paymentEntity.setGatewayAccount(chargeEntity.getGatewayAccount());
        paymentEntity.setReference(chargeEntity.getReference());
        paymentEntity.setReturnUrl(chargeEntity.getReturnUrl());
        paymentEntity.addTransaction(transactionEntity);

        return paymentEntity;
    }
}
