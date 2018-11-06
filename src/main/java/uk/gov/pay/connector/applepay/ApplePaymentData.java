package uk.gov.pay.connector.applepay;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import uk.gov.pay.connector.applepay.api.AppleCardExpiryDate;
import uk.gov.pay.connector.applepay.api.AppleCardExpiryDateDeserialiser;
import uk.gov.pay.connector.applepay.api.PaymentInfo;
import uk.gov.pay.connector.gateway.model.AuthorisationDetails;

import java.util.Map;

public class ApplePaymentData implements AuthorisationDetails {
    private PaymentInfo paymentInfo;
    private String applicationPrimaryAccountNumber;
    private String currencyCode;
    private String transactionAmount;
    private String deviceManufacturerIdentifier;
    private String paymentDataType;
    private Map<String, String> paymentData;
    private String onlinePaymentCryptogram;
    private String eciIndicator;
    private AppleCardExpiryDate applicationExpirationDate; 
    
    public ApplePaymentData() {
    }

    public ApplePaymentData(PaymentInfo paymentInfo, String applicationPrimaryAccountNumber, AppleCardExpiryDate applicationExpirationDate, String currencyCode, String transactionAmount, String deviceManufacturerIdentifier, String paymentDataType, Map<String, String> paymentData) {
        this.paymentInfo = paymentInfo;
        this.applicationPrimaryAccountNumber = applicationPrimaryAccountNumber;
        this.applicationExpirationDate = applicationExpirationDate;
        this.currencyCode = currencyCode;
        this.transactionAmount = transactionAmount;
        this.deviceManufacturerIdentifier = deviceManufacturerIdentifier;
        this.paymentDataType = paymentDataType;
        this.paymentData = paymentData;
        this.onlinePaymentCryptogram = paymentData.get("onlinePaymentCryptogram");
        this.eciIndicator = paymentData.get("eciIndicator");
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

    public Map<String, String> getPaymentData() {
        return paymentData;
    }

    public String getOnlinePaymentCryptogram() {
        return onlinePaymentCryptogram;
    }

    public String getEciIndicator() {
        return eciIndicator;
    }

    public void setPaymentInfo(PaymentInfo paymentInfo) {
        this.paymentInfo = paymentInfo;
    }

    public PaymentInfo getPaymentInfo() {
        return paymentInfo;
    }
}
