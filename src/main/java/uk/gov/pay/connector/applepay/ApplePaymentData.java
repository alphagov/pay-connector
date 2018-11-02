package uk.gov.pay.connector.applepay;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

public class ApplePaymentData {
    private String applicationPrimaryAccountNumber;
    private String applicationExpirationDate;
    private String currencyCode;
    private String transactionAmount;
    private String deviceManufacturerIdentifier;
    private String paymentDataType;
    private Map<String, String> paymentData;


    public String getApplicationPrimaryAccountNumber() {
        return applicationPrimaryAccountNumber;
    }

    public String getApplicationExpirationDate() {
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
}
