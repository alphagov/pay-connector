package uk.gov.pay.connector.wallets.model;

import uk.gov.pay.connector.wallets.WalletType;

import java.time.LocalDate;

public interface WalletAuthorisationData {
    
    WalletPaymentInfo getPaymentInfo();
    WalletTemplateData getWalletTemplateData();
    LocalDate getApplicationExpirationDate();
    WalletType getWalletType();
    
}
