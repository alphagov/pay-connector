package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.model.domain.transaction.TransactionEntity;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static java.util.Arrays.asList;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.defaultGatewayAccountEntity;
import static uk.gov.pay.connector.model.domain.transaction.ChargeTransactionEntityBuilder.aChargeTransactionEntity;
import static uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntityBuilder.aRefundTransactionEntity;

public class PaymentRequestEntityFixture {

    private String externalId = RandomIdGenerator.newId();
    private Long amount = 500L;
    private String returnUrl = "http://return.com";
    private String description = "This is a description";
    private String reference = RandomIdGenerator.newId();
    private GatewayAccountEntity gatewayAccountEntity = defaultGatewayAccountEntity();
    private ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
    private List<TransactionEntity> transactions = asList(aChargeTransactionEntity().build());

    public static PaymentRequestEntityFixture aValidPaymentRequestEntity() {
        return new PaymentRequestEntityFixture();
    }

    public static PaymentRequestEntityFixture aValidPaymentRequestEntityWithRefund() {
        return PaymentRequestEntityFixture.aValidPaymentRequestEntity()
                .withTransactions(aChargeTransactionEntity().build(), aRefundTransactionEntity().build());
    }

    public PaymentRequestEntity build() {
        PaymentRequestEntity entity = new PaymentRequestEntity();
        entity.setReturnUrl(this.returnUrl);
        entity.setReference(this.reference);
        entity.setGatewayAccount(this.gatewayAccountEntity);
        entity.setExternalId(this.externalId);
        entity.setDescription(this.description);
        entity.setCreatedDate(this.createdDate);
        entity.setAmount(this.amount);
        entity.setTransactions(transactions);

        return entity;
    }

    public PaymentRequestEntityFixture withGatewayAccountEntity(GatewayAccountEntity gatewayAccount) {
        this.gatewayAccountEntity = gatewayAccount;
        return this;
    }

    public PaymentRequestEntityFixture withTransactions(TransactionEntity... transactions) {
        this.transactions = Arrays.asList(transactions);
        return this;
    }

    public PaymentRequestEntityFixture withReference(String reference) {
        this.reference = reference;
        return this;
    }

    public PaymentRequestEntityFixture withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public PaymentRequestEntityFixture withAmount(long amount) {
        this.amount = amount;
        return this;
    }
}
