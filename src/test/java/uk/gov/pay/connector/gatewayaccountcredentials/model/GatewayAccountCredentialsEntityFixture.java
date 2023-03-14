package uk.gov.pay.connector.gatewayaccountcredentials.model;

import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.time.Instant;
import java.util.Map;

import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_STRIPE_ACCOUNT_ID;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.RandomIdGenerator.randomUuid;

public final class GatewayAccountCredentialsEntityFixture {
    private Instant activeStartDate = Instant.now();
    private String paymentProvider = WORLDPAY.getName();
    private Map<String, Object> credentials = Map.of();
    private GatewayAccountCredentialState state = ACTIVE;
    private GatewayAccountEntity gatewayAccountEntity;
    private String externalId = randomUuid();
    private Instant createdDate = Instant.now();

    private GatewayAccountCredentialsEntityFixture() {
    }

    public static GatewayAccountCredentialsEntityFixture aGatewayAccountCredentialsEntity() {
        return new GatewayAccountCredentialsEntityFixture();
    }

    public GatewayAccountCredentialsEntityFixture withCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public GatewayAccountCredentialsEntityFixture withActiveStartDate(Instant activeStartDate) {
        this.activeStartDate = activeStartDate;
        return this;
    }

    public GatewayAccountCredentialsEntityFixture withPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
        return this;
    }

    public GatewayAccountCredentialsEntityFixture withCredentials(Map<String, Object> credentials) {
        this.credentials = credentials;
        return this;
    }
    
    public GatewayAccountCredentialsEntityFixture withStripeCredentials() {
        this.credentials = Map.of(CREDENTIALS_STRIPE_ACCOUNT_ID, "acct_abc123");
        return this;
    }

    public GatewayAccountCredentialsEntityFixture withState(GatewayAccountCredentialState state) {
        this.state = state;
        return this;
    }

    public GatewayAccountCredentialsEntityFixture withGatewayAccountEntity(GatewayAccountEntity gatewayAccountEntity) {
        this.gatewayAccountEntity = gatewayAccountEntity;
        return this;
    }

    public GatewayAccountCredentialsEntityFixture withExternalId(String externalId) {
        this.externalId = externalId;
        return this;
    }

    public GatewayAccountCredentialsEntity build() {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = new GatewayAccountCredentialsEntity(gatewayAccountEntity, paymentProvider, credentials, state);
        gatewayAccountCredentialsEntity.setCreatedDate(createdDate);
        gatewayAccountCredentialsEntity.setActiveStartDate(activeStartDate);
        gatewayAccountCredentialsEntity.setExternalId(externalId);
        return gatewayAccountCredentialsEntity;
    }
}
