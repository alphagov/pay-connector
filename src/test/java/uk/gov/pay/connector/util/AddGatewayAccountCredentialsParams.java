package uk.gov.pay.connector.util;

import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;

import java.time.Instant;
import java.util.Map;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public class AddGatewayAccountCredentialsParams {
    private long id;
    private Instant createdDate;
    private Instant activeStartDate;
    private Instant activeEndDate;
    private String paymentProvider;
    private Map<String, Object> credentials;
    private GatewayAccountCredentialState state;
    private String externalId;
    private String lastUpdatedByUserExternalId;
    private long gatewayAccountId;

    public long getId() {
        return id;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public Instant getActiveStartDate() {
        return activeStartDate;
    }

    public Instant getActiveEndDate() {
        return activeEndDate;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public Map<String, Object> getCredentials() {
        return credentials;
    }

    public GatewayAccountCredentialState getState() {
        return state;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getLastUpdatedByUserExternalId() {
        return lastUpdatedByUserExternalId;
    }

    public long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public static final class AddGatewayAccountCredentialsParamsBuilder {
        private long id = secureRandomLong(2, 1000000);
        private Instant createdDate = Instant.now();
        private Instant activeStartDate = null;
        private Instant activeEndDate = null;
        private String paymentProvider = WORLDPAY.getName();
        private Map<String, Object> credentials = Map.of();
        private GatewayAccountCredentialState state = ACTIVE;
        private String externalId = randomUuid();
        private String lastUpdatedByUserExternalId;
        private long gatewayAccountId;

        private AddGatewayAccountCredentialsParamsBuilder() {
        }

        public static AddGatewayAccountCredentialsParamsBuilder anAddGatewayAccountCredentialsParams() {
            return new AddGatewayAccountCredentialsParamsBuilder();
        }

        public AddGatewayAccountCredentialsParamsBuilder withId(long id) {
            this.id = id;
            return this;
        }

        public AddGatewayAccountCredentialsParamsBuilder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public AddGatewayAccountCredentialsParamsBuilder withActiveStartDate(Instant activeStartDate) {
            this.activeStartDate = activeStartDate;
            return this;
        }

        public AddGatewayAccountCredentialsParamsBuilder withActiveEndDate(Instant activeEndDate) {
            this.activeEndDate = activeEndDate;
            return this;
        }

        public AddGatewayAccountCredentialsParamsBuilder withPaymentProvider(String paymentProvider) {
            this.paymentProvider = paymentProvider;
            return this;
        }

        public AddGatewayAccountCredentialsParamsBuilder withCredentials(Map<String, Object> credentials) {
            this.credentials = credentials;
            return this;
        }

        public AddGatewayAccountCredentialsParamsBuilder withState(GatewayAccountCredentialState state) {
            this.state = state;
            return this;
        }

        public AddGatewayAccountCredentialsParamsBuilder withExternalId(String externalId) {
            this.externalId = externalId;
            return this;
        }

        public AddGatewayAccountCredentialsParamsBuilder withLastUpdatedByUserExternalId(String lastUpdatedByUserExternalId) {
            this.lastUpdatedByUserExternalId = lastUpdatedByUserExternalId;
            return this;
        }

        public AddGatewayAccountCredentialsParamsBuilder withGatewayAccountId(long gatewayAccountId) {
            this.gatewayAccountId = gatewayAccountId;
            return this;
        }

        public AddGatewayAccountCredentialsParams build() {
            AddGatewayAccountCredentialsParams addGatewayAccountCredentialsParams = new AddGatewayAccountCredentialsParams();
            addGatewayAccountCredentialsParams.gatewayAccountId = this.gatewayAccountId;
            addGatewayAccountCredentialsParams.state = this.state;
            addGatewayAccountCredentialsParams.lastUpdatedByUserExternalId = this.lastUpdatedByUserExternalId;
            addGatewayAccountCredentialsParams.credentials = this.credentials;
            addGatewayAccountCredentialsParams.activeStartDate = this.activeStartDate;
            addGatewayAccountCredentialsParams.id = this.id;
            addGatewayAccountCredentialsParams.paymentProvider = this.paymentProvider;
            addGatewayAccountCredentialsParams.createdDate = this.createdDate;
            addGatewayAccountCredentialsParams.externalId = this.externalId;
            addGatewayAccountCredentialsParams.activeEndDate = this.activeEndDate;
            return addGatewayAccountCredentialsParams;
        }
    }
}
