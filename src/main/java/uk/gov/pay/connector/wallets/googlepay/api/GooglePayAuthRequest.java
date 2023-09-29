package uk.gov.pay.connector.wallets.googlepay.api;

import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
public interface GooglePayAuthRequest extends WalletAuthorisationRequest {
    @Override
    GooglePayPaymentInfo getPaymentInfo();
}
