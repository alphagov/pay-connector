package uk.gov.pay.connector.wallets;

import java.util.Optional;

public class PaymentData {
    
    public final Optional<String> onlinePaymentCryptogram, eciIndicator;
    public final String worldpayTokenNumber;

    public PaymentData(String onlinePaymentCryptogram, String eciIndicator, String worldpayTokenNumber) {
        this.onlinePaymentCryptogram = Optional.ofNullable(onlinePaymentCryptogram);
        this.eciIndicator = Optional.ofNullable(eciIndicator);
        this.worldpayTokenNumber = worldpayTokenNumber;
    }
}
