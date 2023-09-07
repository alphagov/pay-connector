package uk.gov.pay.connector.wallets;

import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

public interface WalletAuthorisationRequest {
    
    WalletPaymentInfo getPaymentInfo();

    WalletType getWalletType();

}
