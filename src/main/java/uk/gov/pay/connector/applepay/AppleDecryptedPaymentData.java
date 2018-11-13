package uk.gov.pay.connector.applepay;

import uk.gov.pay.connector.applepay.api.AppleCardExpiryDate;
import uk.gov.pay.connector.applepay.api.ApplePaymentInfo;
import uk.gov.pay.connector.gateway.model.AuthorisationDetails;

public class AppleDecryptedPaymentData implements AuthorisationDetails {
    private ApplePaymentInfo paymentInfo;
    private String applicationPrimaryAccountNumber;
    private String currencyCode;
    private long transactionAmount;
    private String deviceManufacturerIdentifier;
    private String paymentDataType;
    private PaymentData paymentData;
    private AppleCardExpiryDate applicationExpirationDate; 
    
    public AppleDecryptedPaymentData() {
    }

    public AppleDecryptedPaymentData(ApplePaymentInfo applePaymentInfo, String applicationPrimaryAccountNumber, AppleCardExpiryDate applicationExpirationDate, String currencyCode, long transactionAmount, String deviceManufacturerIdentifier, String paymentDataType, PaymentData paymentData) {
        this.paymentInfo = applePaymentInfo;
        this.applicationPrimaryAccountNumber = applicationPrimaryAccountNumber;
        this.applicationExpirationDate = applicationExpirationDate;
        this.currencyCode = currencyCode;
        this.transactionAmount = transactionAmount;
        this.deviceManufacturerIdentifier = deviceManufacturerIdentifier;
        this.paymentDataType = paymentDataType;
        this.paymentData = paymentData;
    }

    public String getApplicationPrimaryAccountNumber() {
        return applicationPrimaryAccountNumber;
    }

    public AppleCardExpiryDate getApplicationExpirationDate() {
        return applicationExpirationDate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public long getTransactionAmount() {
        return transactionAmount;
    }

    public String getDeviceManufacturerIdentifier() {
        return deviceManufacturerIdentifier;
    }

    public String getPaymentDataType() {
        return paymentDataType;
    }

    public PaymentData getPaymentData() {
        return paymentData;
    }

    public void setPaymentInfo(ApplePaymentInfo applePaymentInfo) {
        this.paymentInfo = applePaymentInfo;
    }

    public ApplePaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    public static class PaymentData {
        private String onlinePaymentCryptogram;
        private String eciIndicator;

        public PaymentData() {
        }

        public PaymentData(String onlinePaymentCryptogram, String eciIndicator) {
            this.onlinePaymentCryptogram = onlinePaymentCryptogram;
            this.eciIndicator = eciIndicator;
        }

        public String getOnlinePaymentCryptogram() {
            return onlinePaymentCryptogram;
        }

        public String getEciIndicator() {
            return eciIndicator;
        }
    }
}
