package uk.gov.pay.connector.app.adyen;

import uk.gov.pay.connector.app.adyen.ApiKeys.BalancePlatformApiKeys;
import uk.gov.pay.connector.app.adyen.ApiKeys.CompanyAccountApiKeys;
import uk.gov.pay.connector.app.adyen.ApiKeys.LegalEntityManagementApiKeys;

public class ApiKeysFixture {

    private CompanyAccountApiKeys companyAccount = new CompanyAccountApiKeys(
            "test-company-account-API-key",
            "live-company-account-API-key");
    private BalancePlatformApiKeys balancePlatform = new BalancePlatformApiKeys(
            "test-balance-platform-API-key",
            "live-balance-platform-API-key");

    private LegalEntityManagementApiKeys legalEntityManagement = new LegalEntityManagementApiKeys(
            "test-legal-entity-management-API-key",
            "live-legal-entity-management-API-key");

    public static ApiKeysFixture anApiKeys() {
        return new ApiKeysFixture();
    }

    public ApiKeysFixture withCompanyAccount(CompanyAccountApiKeys companyAccount) {
        this.companyAccount = companyAccount;
        return this;
    }

    public ApiKeysFixture withBalancePlatform(BalancePlatformApiKeys balancePlatform) {
        this.balancePlatform = balancePlatform;
        return this;
    }

    public ApiKeysFixture withLegalEntityManagement(LegalEntityManagementApiKeys legalEntityManagement) {
        this.legalEntityManagement = legalEntityManagement;
        return this;
    }

    public ApiKeys build() {
        return new ApiKeys(companyAccount, balancePlatform, legalEntityManagement);
    }
}
