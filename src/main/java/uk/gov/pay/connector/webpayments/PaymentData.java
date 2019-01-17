package uk.gov.pay.connector.webpayments;

public class PaymentData {
    
    public final String onlinePaymentCryptogram, eciIndicator;

    public PaymentData(String onlinePaymentCryptogram, String eciIndicator) {
        this.onlinePaymentCryptogram = onlinePaymentCryptogram;
        this.eciIndicator = eciIndicator;
    }
}
