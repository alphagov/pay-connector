package uk.gov.pay.connector.applepay;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import uk.gov.pay.connector.applepay.api.AppleCardExpiryDate;
import uk.gov.pay.connector.applepay.api.AppleCardExpiryDateDeserialiser;
import uk.gov.pay.connector.applepay.api.PaymentInfo;
import uk.gov.pay.connector.gateway.model.AuthorisationDetails;

public class AppleDecryptedPaymentData implements AuthorisationDetails {
    private PaymentInfo paymentInfo;
    private String applicationPrimaryAccountNumber;
    private String currencyCode;
    private String transactionAmount;
    private String deviceManufacturerIdentifier;
    private String paymentDataType;
    private PaymentData paymentData;
    private AppleCardExpiryDate applicationExpirationDate; 
    
    public AppleDecryptedPaymentData() {
    }

    public AppleDecryptedPaymentData(PaymentInfo paymentInfo, String applicationPrimaryAccountNumber, AppleCardExpiryDate applicationExpirationDate, String currencyCode, String transactionAmount, String deviceManufacturerIdentifier, String paymentDataType, PaymentData paymentData) {
        this.paymentInfo = paymentInfo;
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

    @JsonDeserialize(using = AppleCardExpiryDateDeserialiser.class)
    public AppleCardExpiryDate getApplicationExpirationDate() {
        return applicationExpirationDate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getTransactionAmount() {
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

    public void setPaymentInfo(PaymentInfo paymentInfo) {
        this.paymentInfo = paymentInfo;
    }

    public PaymentInfo getPaymentInfo() {
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
