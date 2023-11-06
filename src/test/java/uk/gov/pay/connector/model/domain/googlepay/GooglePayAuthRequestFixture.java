package uk.gov.pay.connector.model.domain.googlepay;

import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayPaymentInfo;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import static uk.gov.pay.connector.model.domain.googlepay.GooglePayPaymentInfoFixture.aGooglePayPaymentInfo;

public final class GooglePayAuthRequestFixture {
    private GooglePayPaymentInfo googlePaymentInfo = aGooglePayPaymentInfo().build();
    private String googlePaymentData = "***ENCRYPTED***DATA***";
    
    private GooglePayAuthRequestFixture() {
    }

    public static GooglePayAuthRequestFixture aGooglePayAuthRequest() {
        return new GooglePayAuthRequestFixture();
    }

    public WalletPaymentInfo getGooglePaymentInfo() {
        return googlePaymentInfo;
    }

    public GooglePayAuthRequestFixture withGooglePaymentInfo(GooglePayPaymentInfo googlePaymentInfo) {
        this.googlePaymentInfo = googlePaymentInfo;
        return this;
    }

    public String getGooglePaymentData() { return googlePaymentData; }

    public GooglePayAuthRequestFixture withGooglePaymentData(String googlePaymentData) {
        this.googlePaymentData = googlePaymentData;
        return this;
    }

    public GooglePayAuthRequest build() {
        return new GooglePayAuthRequest(
                googlePaymentInfo,
                googlePaymentData);
    }
}
