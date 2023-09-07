package uk.gov.pay.connector.model.domain.applepay;

import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

public final class ApplePayAuthRequestFixture {
    private WalletPaymentInfo applePaymentInfo = new WalletPaymentInfo(
            "4242",
            "visa",
            PayersCardType.DEBIT,
            "Mr. Payment",
            "aaa@bbb.test"
    );
    private String applePaymentData = "***ENCRYPTED***DATA***";
    
    private ApplePayAuthRequestFixture() {
    }

    public static ApplePayAuthRequestFixture anApplePayAuthRequest() {
        return new ApplePayAuthRequestFixture();
    }

    public WalletPaymentInfo getApplePaymentInfo() {
        return applePaymentInfo;
    }

    public ApplePayAuthRequestFixture withApplePaymentInfo(WalletPaymentInfo applePaymentInfo) {
        this.applePaymentInfo = applePaymentInfo;
        return this;
    }

    public String getApplePaymentData() { return applePaymentData; }

    public ApplePayAuthRequestFixture withApplePaymentData(String applePaymentData) {
        this.applePaymentData = applePaymentData;
        return this;
    }

    public ApplePayAuthRequest build() {
        return new ApplePayAuthRequest(
                applePaymentInfo,
                applePaymentData);
    }
}
