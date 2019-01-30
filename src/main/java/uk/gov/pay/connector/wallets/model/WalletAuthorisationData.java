package uk.gov.pay.connector.wallets.model;

import uk.gov.pay.connector.wallets.WalletType;

import java.time.LocalDate;
import java.util.Optional;

public interface WalletAuthorisationData {
    
    WalletPaymentInfo getPaymentInfo();
    Optional<LocalDate> getCardExpiryDate();
    WalletType getWalletType();
    String getLastDigitsCardNumber();
    
}
