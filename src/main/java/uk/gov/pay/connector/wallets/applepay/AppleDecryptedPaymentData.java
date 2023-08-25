package uk.gov.pay.connector.wallets.applepay;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import uk.gov.pay.connector.gateway.model.AuthorisationDetails;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

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

    public AppleDecryptedPaymentData(WalletPaymentInfo applePaymentInfo, String applicationPrimaryAccountNumber,
                                     LocalDate applicationExpirationDate, String currencyCode, long transactionAmount,
                                     String deviceManufacturerIdentifier, String paymentDataType, PaymentData paymentData) {
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

    public Optional<LocalDate> getCardExpiryDate() {
        return Optional.ofNullable(applicationExpirationDate);
    }

    @Override
    public String getLastDigitsCardNumber() {
        return paymentInfo.getLastDigitsCardNumber();
    }

    public String getExpiryDateMonth() {
        return applicationExpirationDate.format(DateTimeFormatter.ofPattern("MM"));
    }

    public String getExpiryDateYear() {
        return applicationExpirationDate.format(DateTimeFormatter.ofPattern("yyyy"));
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
