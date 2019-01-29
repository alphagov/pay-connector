package uk.gov.pay.connector.wallets.model;

import uk.gov.pay.connector.wallets.WalletType;

import java.time.LocalDate;

public interface WalletAuthorisationData {
    
    WalletPaymentInfo getPaymentInfo();
    LocalDate getCardExpiryDate();
    WalletType getWalletType();
    String getLastDigitsCardNumber();
    
}
