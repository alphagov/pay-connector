package uk.gov.pay.connector.app.adyen;

import uk.gov.pay.connector.app.adyen.BaseUrls.BalancePlatformUrls;
import uk.gov.pay.connector.app.adyen.BaseUrls.CheckoutUrls;
import uk.gov.pay.connector.app.adyen.BaseUrls.LegalEntityManagementUrls;
import uk.gov.pay.connector.app.adyen.BaseUrls.ManagementUrls;

public class BaseUrlsFixture {

    private CheckoutUrls checkout = new CheckoutUrls(
            "https://checkout-test.example.com", "https://checkout.example.com");
    private BalancePlatformUrls balancePlatform = new BalancePlatformUrls(
            "https://balance-test.example.com");
    private LegalEntityManagementUrls legalEntityManagement = new LegalEntityManagementUrls(
            "https://legal-test.example.com");
    private ManagementUrls management = new ManagementUrls(
            "https://management-test.example.com");

    public static BaseUrlsFixture someBaseUrls() {
        return new BaseUrlsFixture();
    }

    public BaseUrlsFixture withCheckout(CheckoutUrls checkout) {
        this.checkout = checkout;
        return this;
    }

    public BaseUrls build() {
        return new BaseUrls(checkout,  balancePlatform, legalEntityManagement, management);
    }
}
