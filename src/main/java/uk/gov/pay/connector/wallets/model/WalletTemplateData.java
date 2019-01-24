package uk.gov.pay.connector.wallets.model;

import uk.gov.pay.connector.wallets.WalletType;

public interface WalletTemplateData {
    
    String getLastDigitsCardNumber();
    WalletType getWalletType();
}
