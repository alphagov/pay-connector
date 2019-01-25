package uk.gov.pay.connector.wallets.applepay;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import uk.gov.pay.connector.gateway.model.AuthorisationDetails;
import uk.gov.pay.connector.gateway.worldpay.applepay.ApplePayTemplateData;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;
import uk.gov.pay.connector.wallets.model.WalletTemplateData;

import java.time.LocalDate;

public class AppleDecryptedPaymentData implements AuthorisationDetails, WalletAuthorisationData {
    private WalletPaymentInfo paymentInfo;
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

    public AppleDecryptedPaymentData(WalletPaymentInfo applePaymentInfo, String applicationPrimaryAccountNumber, LocalDate applicationExpirationDate, String currencyCode, long transactionAmount, String deviceManufacturerIdentifier, String paymentDataType, PaymentData paymentData) {
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

    public LocalDate getCardExpiryDate() {
        return applicationExpirationDate;
    }

    @Override
    public WalletType getWalletType() {
        return WalletType.APPLE_PAY;
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

    public void setPaymentInfo(WalletPaymentInfo applePaymentInfo) {
        this.paymentInfo = applePaymentInfo;
    }
    
    @Override
    public WalletPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    @Override
    public WalletTemplateData getWalletTemplateData() {
        return ApplePayTemplateData.from(this);
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
