package uk.gov.pay.connector.model.spike;

import static uk.gov.pay.connector.model.domain.GatewayAccountEntity.Type.TEST;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.model.domain.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.EmailNotificationEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.spike.TransactionEntity.TransactionOperation;
import uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus;
import uk.gov.pay.connector.util.RandomIdGenerator;

public class TransactionEntityFixture {

    private Long id = 1L;
    private Long amount = 500L;
    private String email = "test@email.com";
    private TransactionOperation operation = TransactionOperation.CHARGE;
    private TransactionStatus status = TransactionStatus.CREATED;
    private PaymentRequestEntity paymentRequestEntity = defaultPaymentRequestEntity();
    private Auth3dsDetailsEntity auth3dsDetailsEntity;
    private CardDetailsEntity cardDetailsEntity = new CardDetailsEntity();
    private String gatewayTransactionId;
    private ZonedDateTime createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
    private List<TransactionEventEntity> events = new ArrayList<>();

    public static TransactionEntityFixture aValidChargeEntity() {
        return new TransactionEntityFixture();
    }

    public ChargeEntityNew build() {
        ChargeEntityNew transactionEntity = new ChargeEntityNew(status, amount, createdDate, cardDetailsEntity, events, paymentRequestEntity, gatewayTransactionId, email, ZonedDateTime.now(), auth3dsDetailsEntity);
        return transactionEntity;
    }

    public void setEvents(List<TransactionEventEntity> events) {
        this.events = events;
    }

    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public void setPaymentRequestEntity(
        PaymentRequestEntity paymentRequestEntity) {

        this.paymentRequestEntity = paymentRequestEntity;
    }

    public static PaymentRequestEntity defaultPaymentRequestEntity() {
        PaymentRequestEntity paymentRequestEntity = PaymentRequestEntityFixture.defaultPaymentRequestEntity();
        return paymentRequestEntity;
    }
    public TransactionEntityFixture withId(Long id) {
        this.id = id;
        return this;
    }

    public TransactionEntityFixture withOperation(TransactionOperation operation) {
        this.operation = operation;
        return this;
    }
    public TransactionEntityFixture withPaymentRequestEntity(PaymentRequestEntity paymentRequestEntity) {
        this.paymentRequestEntity = paymentRequestEntity;
        return this;
    }
    public TransactionEntityFixture withAmount(Long amount) {
        this.amount = amount;
        return this;
    }

    public TransactionEntityFixture withStatus(TransactionStatus status) {
        this.status = status;
        return this;
    }

    public TransactionEntityFixture withEvents(List<TransactionEventEntity> events) {
        this.events = events;
        return this;
    }

    public TransactionEntityFixture withCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public TransactionEntityFixture withAuth3dsDetailsEntity(Auth3dsDetailsEntity auth3dsDetailsEntity) {
        this.auth3dsDetailsEntity = auth3dsDetailsEntity;
        return this;
    }
}
