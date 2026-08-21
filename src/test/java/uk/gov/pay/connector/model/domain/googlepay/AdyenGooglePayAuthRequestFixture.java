package uk.gov.pay.connector.model.domain.googlepay;

import uk.gov.pay.connector.wallets.googlepay.api.AdyenGooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayPaymentInfo;

public final class AdyenGooglePayAuthRequestFixture {
    private GooglePayPaymentInfo googlePaymentInfo;
    
    private AdyenGooglePayAuthRequestFixture() {
    }

    public static AdyenGooglePayAuthRequestFixture aGooglePayAuthRequest() {
        return new AdyenGooglePayAuthRequestFixture();
    }

    public AdyenGooglePayAuthRequest build() {
        return new AdyenGooglePayAuthRequest(
                googlePaymentInfo,
                "token"
        );
    }

    public AdyenGooglePayAuthRequestFixture withGooglePaymentInfo(GooglePayPaymentInfo googlePaymentInfo) {
        this.googlePaymentInfo = googlePaymentInfo;
        return this;
    }
}
