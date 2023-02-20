package uk.gov.pay.connector.paymentinstrument.model;

import uk.gov.pay.connector.charge.model.CardDetailsEntity;

import java.time.Instant;
import java.util.Map;

public final class PaymentInstrumentEntityFixture {
    private Map<String, String> recurringAuthToken;
    private Instant createdDate = Instant.now();
    private Instant startDate = Instant.now();
    private String externalId = "a-payment-instrument-external-id";
    private PaymentInstrumentStatus paymentInstrumentStatus = PaymentInstrumentStatus.CREATED;
    private CardDetailsEntity cardDetails;

    private PaymentInstrumentEntityFixture() {
    }

    public static PaymentInstrumentEntityFixture aPaymentInstrumentEntity() {
        return new PaymentInstrumentEntityFixture();
    }

    public PaymentInstrumentEntityFixture withRecurringAuthToken(Map<String, String> recurringAuthToken) {
        this.recurringAuthToken = recurringAuthToken;
        return this;
    }

    public PaymentInstrumentEntityFixture withCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public PaymentInstrumentEntityFixture withStartDate(Instant startDate) {
        this.startDate = startDate;
        return this;
    }

    public PaymentInstrumentEntityFixture withExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public PaymentInstrumentEntityFixture withPaymentInstrumentStatus(PaymentInstrumentStatus paymentInstrumentStatus) {
        this.paymentInstrumentStatus = paymentInstrumentStatus;
        return this;
    }

    public PaymentInstrumentEntityFixture withCardDetails(CardDetailsEntity cardDetails) {
        this.cardDetails = cardDetails;
        return this;
    }

    public PaymentInstrumentEntity build() {
        PaymentInstrumentEntity paymentInstrumentEntity = new PaymentInstrumentEntity();
        paymentInstrumentEntity.setRecurringAuthToken(recurringAuthToken);
        paymentInstrumentEntity.setCreatedDate(createdDate);
        paymentInstrumentEntity.setStartDate(startDate);
        paymentInstrumentEntity.setExternalId(externalId);
        paymentInstrumentEntity.setPaymentInstrumentStatus(paymentInstrumentStatus);
        paymentInstrumentEntity.setCardDetails(cardDetails);
        return paymentInstrumentEntity;
    }
}
