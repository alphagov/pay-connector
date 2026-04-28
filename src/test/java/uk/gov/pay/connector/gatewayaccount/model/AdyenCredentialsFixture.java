package uk.gov.pay.connector.gatewayaccount.model;

public class AdyenCredentialsFixture {

    private String legalEntityId = "legal-entity-id";
    private String storeId = "store-id";

    static AdyenCredentialsFixture anAdyenCredentials() {
        return new AdyenCredentialsFixture();
    }

    public AdyenCredentials build() {
        return new AdyenCredentials(legalEntityId, storeId);
    }

    public AdyenCredentialsFixture withLegalEntityId(String legalEntityId) {
        this.legalEntityId = legalEntityId;
        return this;
    }

    public AdyenCredentialsFixture withStoreId(String storeId) {
        this.storeId = storeId;
        return this;
    }
}
