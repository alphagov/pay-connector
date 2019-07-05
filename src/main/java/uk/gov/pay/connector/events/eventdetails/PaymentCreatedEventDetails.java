package uk.gov.pay.connector.events.eventdetails;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

import java.util.Objects;

public class PaymentCreatedEventDetails extends EventDetails {
    private final Long amount;
    private final String description;
    private final String reference;
    private final String returnUrl;
    private final Long gatewayAccountId;
    private final String paymentProvider;
    private final String language;
    private final boolean delayedCapture;

    public PaymentCreatedEventDetails(Long amount, String description, String reference, String returnUrl,
                                      Long gatewayAccountId, String paymentProvider, String language,
                                      boolean delayedCapture) {
        this.amount = amount;
        this.description = description;
        this.reference = reference;
        this.returnUrl = returnUrl;
        this.gatewayAccountId = gatewayAccountId;
        this.paymentProvider = paymentProvider;
        this.language = language;
        this.delayedCapture = delayedCapture;
    }

    public static PaymentCreatedEventDetails from(ChargeEntity charge) {
        return new PaymentCreatedEventDetails(
                charge.getAmount(),
                charge.getDescription(),
                charge.getReference().toString(),
                charge.getReturnUrl(),
                charge.getGatewayAccount().getId(),
                charge.getGatewayAccount().getGatewayName(),
                charge.getLanguage().toString(),
                charge.isDelayedCapture());
    }

    public Long getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isDelayedCapture() {
        return delayedCapture;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentCreatedEventDetails that = (PaymentCreatedEventDetails) o;
        return Objects.equals(amount, that.amount) &&
                Objects.equals(description, that.description) &&
                Objects.equals(reference, that.reference) &&
                Objects.equals(returnUrl, that.returnUrl) &&
                Objects.equals(gatewayAccountId, that.gatewayAccountId) &&
                Objects.equals(paymentProvider, that.paymentProvider) &&
                Objects.equals(language, that.language) &&
                Objects.equals(delayedCapture, that.delayedCapture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, description, reference, returnUrl, gatewayAccountId, paymentProvider, language, delayedCapture);
    }
}
