package uk.gov.pay.connector.applepay;

import uk.gov.pay.connector.gateway.model.AuthorisationDetails;

import java.util.Map;

public class ApplePaymentData implements AuthorisationDetails {
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
