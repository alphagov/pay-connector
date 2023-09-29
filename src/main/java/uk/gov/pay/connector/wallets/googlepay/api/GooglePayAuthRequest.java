package uk.gov.pay.connector.wallets.googlepay.api;

import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

public interface GooglePayAuthRequest extends WalletAuthorisationRequest {
    @Override
    WalletPaymentInfo getPaymentInfo();
}
