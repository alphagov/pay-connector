package uk.gov.pay.connector.gateway.worldpay.applepay;

import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.model.WalletTemplateData;

import java.time.format.DateTimeFormatter;

public class ApplePayTemplateData implements WalletTemplateData {
    private String applicationPrimaryAccountNumber;
    private String expiryDateMonth;
    private String expiryDateYear;
    private String cardholderName;
    private String onlinePaymentCryptogram;
    private String eciIndicator;
    private String lastDigitsCardNumber;

    private ApplePayTemplateData(String applicationPrimaryAccountNumber, String expiryDateMonth, String expiryDateYear, String cardholderName, String onlinePaymentCryptogram, String eciIndicator, String lastDigitsCardNumber) {
        this.applicationPrimaryAccountNumber = applicationPrimaryAccountNumber;
        this.expiryDateMonth = expiryDateMonth;
        this.expiryDateYear = expiryDateYear;
        this.cardholderName = cardholderName;
        this.onlinePaymentCryptogram = onlinePaymentCryptogram;
        this.eciIndicator = eciIndicator;
        this.lastDigitsCardNumber = lastDigitsCardNumber;
    }

    public static ApplePayTemplateData from(AppleDecryptedPaymentData appleDecryptedPaymentData) {
        return new ApplePayTemplateData(
                appleDecryptedPaymentData.getApplicationPrimaryAccountNumber(), 
                appleDecryptedPaymentData.getCardExpiryDate().format(DateTimeFormatter.ofPattern("MM")),
                appleDecryptedPaymentData.getCardExpiryDate().format(DateTimeFormatter.ofPattern("yyyy")),
                appleDecryptedPaymentData.getPaymentInfo().getCardholderName(),
                appleDecryptedPaymentData.getPaymentData().getOnlinePaymentCryptogram(),
                appleDecryptedPaymentData.getPaymentData().getEciIndicator(),
                appleDecryptedPaymentData.getPaymentInfo().getLastDigitsCardNumber());
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

    @Override
    public String getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    @Override
    public WalletType getWalletType() {
        return WalletType.APPLE_PAY;
    }
}
