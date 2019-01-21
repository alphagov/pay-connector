package uk.gov.pay.connector.gateway.worldpay.applepay;

import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;

import java.time.format.DateTimeFormatter;

public class ApplePayTemplateData {
    private String applicationPrimaryAccountNumber;
    private String expiryDateMonth;
    private String expiryDateYear;
    private String cardholderName;
    private String onlinePaymentCryptogram;
    private String eciIndicator;

    private ApplePayTemplateData(String applicationPrimaryAccountNumber, String expiryDateMonth, String expiryDateYear, String cardholderName, String onlinePaymentCryptogram, String eciIndicator) {
        this.applicationPrimaryAccountNumber = applicationPrimaryAccountNumber;
        this.expiryDateMonth = expiryDateMonth;
        this.expiryDateYear = expiryDateYear;
        this.cardholderName = cardholderName;
        this.onlinePaymentCryptogram = onlinePaymentCryptogram;
        this.eciIndicator = eciIndicator;
    }

    public static ApplePayTemplateData from(AppleDecryptedPaymentData appleDecryptedPaymentData) {
        return new ApplePayTemplateData(
                appleDecryptedPaymentData.getApplicationPrimaryAccountNumber(), 
                appleDecryptedPaymentData.getApplicationExpirationDate().format(DateTimeFormatter.ofPattern("MM")),
                appleDecryptedPaymentData.getApplicationExpirationDate().format(DateTimeFormatter.ofPattern("yyyy")),
                appleDecryptedPaymentData.getPaymentInfo().getCardholderName(),
                appleDecryptedPaymentData.getPaymentData().getOnlinePaymentCryptogram(),
                appleDecryptedPaymentData.getPaymentData().getEciIndicator()
        );
    }

    public String getApplicationPrimaryAccountNumber() {
        return applicationPrimaryAccountNumber;
    }

    public String getExpiryDateMonth() {
        return expiryDateMonth;
    }

    public String getExpiryDateYear() {
        return expiryDateYear;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public String getOnlinePaymentCryptogram() {
        return onlinePaymentCryptogram;
    }

    public String getEciIndicator() {
        return eciIndicator;
    }
}
