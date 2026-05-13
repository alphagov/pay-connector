package uk.gov.pay.connector.gatewayaccount.model;

public class AdyenCredentialsFixture {

    private String legalEntityId = "legal-entity-id";
    private String storeId = "store-id";
    private String accountHolderId = "account-holder-id";
    private String balanceAccountId = "balance-account-id";
    
    static AdyenCredentialsFixture anAdyenCredentials() {
        return new AdyenCredentialsFixture();
    }

    public AdyenCredentials build() {
        return new AdyenCredentials(legalEntityId, storeId, accountHolderId, balanceAccountId);
    }

    public AdyenCredentialsFixture withLegalEntityId(String legalEntityId) {
        this.legalEntityId = legalEntityId;
        return this;
    }

    public AdyenCredentialsFixture withStoreId(String storeId) {
        this.storeId = storeId;
        return this;
    }
    
    public AdyenCredentialsFixture withAccountHolderId(String accountHolderId) {
        this.accountHolderId = accountHolderId;
        return this;
    }

    public AdyenCredentialsFixture withBalanceAccountId(String balanceAccountId) {
        this.balanceAccountId = balanceAccountId;
        return this;
    }
}
