package uk.gov.pay.connector.paymentinstrument.model;

import uk.gov.pay.connector.card.model.CardDetailsEntity;

import java.time.Instant;
import java.util.Map;

import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY;
import static uk.gov.pay.connector.gateway.stripe.StripeAuthorisationResponse.STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY;

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

    public PaymentInstrumentEntityFixture withStripeRecurringAuthToken(String customerId, String paymentMethodId) {
        this.recurringAuthToken = Map.of(
                STRIPE_RECURRING_AUTH_TOKEN_CUSTOMER_ID_KEY, customerId,
                STRIPE_RECURRING_AUTH_TOKEN_PAYMENT_METHOD_ID_KEY, paymentMethodId);
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
        paymentInstrumentEntity.setStatus(paymentInstrumentStatus);
        paymentInstrumentEntity.setCardDetails(cardDetails);
        return paymentInstrumentEntity;
    }
}
