package uk.gov.pay.connector.model.domain.applepay;

import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayPaymentInfo;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import static uk.gov.pay.connector.model.domain.applepay.ApplePayPaymentInfoFixture.anApplePayPaymentInfo;

public final class ApplePayAuthRequestFixture {
    private ApplePayPaymentInfo applePaymentInfo = anApplePayPaymentInfo().build();
    private String applePaymentData = "***ENCRYPTED***DATA***";

    private ApplePayAuthRequestFixture() {
    }

    public static ApplePayAuthRequestFixture anApplePayAuthRequest() {
        return new ApplePayAuthRequestFixture();
    }

    public WalletPaymentInfo getApplePaymentInfo() {
        return applePaymentInfo;
    }

    public ApplePayAuthRequestFixture withApplePaymentInfo(ApplePayPaymentInfo applePaymentInfo) {
        this.applePaymentInfo = applePaymentInfo;
        return this;
    }

    public String getApplePaymentData() {
        return applePaymentData;
    }

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
