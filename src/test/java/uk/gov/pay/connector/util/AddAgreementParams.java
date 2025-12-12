package uk.gov.pay.connector.util;

import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

import static uk.gov.pay.connector.util.RandomIdGenerator.randomLong;

public class AddAgreementParams {

    private final Long agreementId;
    private final String externalAgreementId;
    private final boolean live;
    private final String serviceId;
    private final String gatewayAccountId;
    private final Instant createdDate;
    private final String reference;
    private final String description;
    private final String userIdentifier;
    private final Long paymentInstrumentId;

    public Long getAgreementId() {
        return agreementId;
    }

    public String getExternalAgreementId() {
        return externalAgreementId;
    }

    public boolean isLive() {
        return live;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getGatewayAccountId() {
        return gatewayAccountId;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public String getUserIdentifier() {
        return userIdentifier;
    }

    public Long getPaymentInstrumentId() {
        return paymentInstrumentId;
    }

    private AddAgreementParams(AddAgreementParamsBuilder builder) {
        agreementId = builder.agreementId;
        externalAgreementId = builder.externalAgreementId;
        live = builder.live;
        serviceId = builder.serviceId;
        gatewayAccountId = builder.gatewayAccountId;
        createdDate = builder.createdDate;
        reference = builder.reference;
        description = builder.description;
        userIdentifier = builder.userIdentifier;
        paymentInstrumentId = builder.paymentInstrumentId;
    }

    public static final class AddAgreementParamsBuilder {
        private Long agreementId = randomLong();
        private String externalAgreementId = "anExternalAgreementId";
        private boolean live = false;
        private String serviceId = "aServiceId";
        private String gatewayAccountId;
        private Instant createdDate = Instant.now();
        private String reference = "Test reference";
        private String description = "Test description";
        private String userIdentifier = "Test user identifier";
        private Long paymentInstrumentId;

        private AddAgreementParamsBuilder() {
        }

        public static AddAgreementParamsBuilder anAddAgreementParams() {
            return new AddAgreementParamsBuilder();
        }

        public AddAgreementParamsBuilder withAgreementId(Long agreementId) {
            this.agreementId = agreementId;
            return this;
        }

        public AddAgreementParamsBuilder withExternalAgreementId(String externalAgreementId) {
            this.externalAgreementId = externalAgreementId;
            return this;
        }

        public AddAgreementParamsBuilder withLive(boolean live) {
            this.live = live;
            return this;
        }

        public AddAgreementParamsBuilder withServiceId(String serviceId) {
            this.serviceId = serviceId;
            return this;
        }

        public AddAgreementParamsBuilder withGatewayAccountId(String gatewayAccountId) {
            this.gatewayAccountId = gatewayAccountId;
            return this;
        }

        public AddAgreementParamsBuilder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public AddAgreementParamsBuilder withReference(String reference) {
            this.reference = reference;
            return this;
        }

        public AddAgreementParamsBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public AddAgreementParamsBuilder withUserIdentifier(String userIdentifier) {
            this.userIdentifier = userIdentifier;
            return this;
        }

        public AddAgreementParamsBuilder withPaymentInstrumentId(Long paymentInstrumentId) {
            this.paymentInstrumentId = paymentInstrumentId;
            return this;
        }

        public AddAgreementParams build() {
            Stream.of(agreementId, live, serviceId, gatewayAccountId, reference, description, createdDate).forEach(Objects::requireNonNull);
            return new AddAgreementParams(this);
        }

    }

}
