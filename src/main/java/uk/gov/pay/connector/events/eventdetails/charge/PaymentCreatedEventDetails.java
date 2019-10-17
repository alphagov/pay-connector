package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Map;
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
    private final boolean live;
    private final Map<String, Object> externalMetadata;

    public PaymentCreatedEventDetails(Builder builder) {
        this.amount = builder.amount;
        this.description = builder.description;
        this.reference = builder.reference;
        this.returnUrl = builder.returnUrl;
        this.gatewayAccountId = builder.gatewayAccountId;
        this.paymentProvider = builder.paymentProvider;
        this.language = builder.language;
        this.delayedCapture = builder.delayedCapture;
        this.live = builder.live;
        this.externalMetadata = builder.externalMetadata;
    }

    public static PaymentCreatedEventDetails from(ChargeEntity charge) {
        return new Builder()
                .withAmount(charge.getAmount())
                .withDescription(charge.getDescription())
                .withReference(charge.getReference().toString())
                .withReturnUrl(charge.getReturnUrl())
                .withGatewayAccountId(charge.getGatewayAccount().getId())
                .withPaymentProvider(charge.getGatewayAccount().getGatewayName())
                .withLanguage(charge.getLanguage().toString())
                .withDelayedCapture(charge.isDelayedCapture())
                .withLive(charge.getGatewayAccount().isLive())
                .withExternalMetadata(charge.getExternalMetadata().map(ExternalMetadata::getMetadata).orElse(null))
                .build();
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

    public String getGatewayAccountId() {
        return gatewayAccountId.toString();
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

    public boolean isLive() {
        return live;
    }

    public Map<String, Object> getExternalMetadata() {
        return externalMetadata;
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
                Objects.equals(delayedCapture, that.delayedCapture) &&
                Objects.equals(live, that.live) &&
                Objects.equals(externalMetadata, that.externalMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, description, reference, returnUrl, gatewayAccountId, paymentProvider, language,
                delayedCapture, live, externalMetadata);
    }

    public static class Builder {
        private Long amount;
        private String description;
        private String reference;
        private String returnUrl;
        private Long gatewayAccountId;
        private String paymentProvider;
        private String language;
        private boolean delayedCapture;
        private boolean live;
        private Map<String, Object> externalMetadata;

        public PaymentCreatedEventDetails build() {
            return new PaymentCreatedEventDetails(this);
        }

        public Builder withAmount(Long amount) {
            this.amount = amount;
            return this;
        }

        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder withReference(String reference) {
            this.reference = reference;
            return this;
        }

        public Builder withReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return this;
        }

        public Builder withGatewayAccountId(Long gatewayAccountId) {
            this.gatewayAccountId = gatewayAccountId;
            return this;
        }

        public Builder withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
            return this;
        }

        public Builder withLanguage(String language) {
            this.language = language;
            return this;
        }

        public Builder withDelayedCapture(boolean delayedCapture) {
            this.delayedCapture = delayedCapture;
            return this;
        }

        public Builder withLive(boolean live) {
            this.live = live;
            return this;
        }

        public Builder withExternalMetadata(Map<String, Object> externalMetadata) {
            this.externalMetadata = externalMetadata;
            return this;
        }
    }
}
