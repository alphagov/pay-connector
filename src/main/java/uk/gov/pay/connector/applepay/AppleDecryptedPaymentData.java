package uk.gov.pay.connector.applepay;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import uk.gov.pay.connector.common.model.api.PaymentInfo;
import uk.gov.pay.connector.gateway.model.AuthorisationDetails;

import java.time.LocalDate;

public class AppleDecryptedPaymentData implements AuthorisationDetails {
    private PaymentInfo paymentInfo;
    private String applicationPrimaryAccountNumber;
    private String currencyCode;
    private long transactionAmount;
    private String deviceManufacturerIdentifier;
    private String paymentDataType;
    private PaymentData paymentData;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyMMdd")
    @JsonDeserialize(using = LocalDateDeserializer.class)
    private LocalDate applicationExpirationDate; 
    
    public AppleDecryptedPaymentData() {
    }

    public AppleDecryptedPaymentData(PaymentInfo applePaymentInfo, String applicationPrimaryAccountNumber, LocalDate applicationExpirationDate, String currencyCode, long transactionAmount, String deviceManufacturerIdentifier, String paymentDataType, PaymentData paymentData) {
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

    public LocalDate getApplicationExpirationDate() {
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

    public void setPaymentInfo(PaymentInfo applePaymentInfo) {
        this.paymentInfo = applePaymentInfo;
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
