package uk.gov.pay.connector.model.domain.applepay;

import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.time.LocalDate;

import static uk.gov.pay.connector.model.domain.applepay.WalletPaymentInfoFixture.aWalletPaymentInfo;

public final class ApplePayDecryptedPaymentDataFixture {
    private WalletPaymentInfo applePaymentInfo = aWalletPaymentInfo().build();
    private String applicationPrimaryAccountNumber = "4818528840010767";
    private LocalDate applicationExpirationDate = LocalDate.of(2023, 12, 1);
    private String currencyCode = "643";
    private Long transactionAmount = 10L;
    private String deviceManufacturerIdentifier = "040010030273";
    private String paymentDataType = "3DSecure";
    private String onlinePaymentCryptogram = "Ao/fzpIAFvp1eB9y8WVDMAACAAA=";
    private String eciIndicator = "7";

    private ApplePayDecryptedPaymentDataFixture() {
    }

    public static ApplePayDecryptedPaymentDataFixture anApplePayDecryptedPaymentData() {
        return new ApplePayDecryptedPaymentDataFixture();
    }

    public WalletPaymentInfo getApplePaymentInfo() {
        return applePaymentInfo;
    }

    public ApplePayDecryptedPaymentDataFixture withApplePaymentInfo(WalletPaymentInfo applePaymentInfo) {
        this.applePaymentInfo = applePaymentInfo;
        return this;
    }

    public String getApplicationPrimaryAccountNumber() {
        return applicationPrimaryAccountNumber;
    }

    public ApplePayDecryptedPaymentDataFixture withApplicationPrimaryAccountNumber(String applicationPrimaryAccountNumber) {
        this.applicationPrimaryAccountNumber = applicationPrimaryAccountNumber;
        return this;
    }

    public LocalDate getApplicationExpirationDate() {
        return applicationExpirationDate;
    }

    public ApplePayDecryptedPaymentDataFixture withExpiryDate(LocalDate expiryDate) {
        this.applicationExpirationDate = expiryDate;
        return this;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public ApplePayDecryptedPaymentDataFixture withConcurrencyCode(String concurrencyCode) {
        this.currencyCode = concurrencyCode;
        return this;
    }

    public Long getTransactionAmount() {
        return transactionAmount;
    }

    public ApplePayDecryptedPaymentDataFixture withAmount(Long amount) {
        this.transactionAmount = amount;
        return this;
    }

    public String getDeviceManufacturerIdentifier() {
        return deviceManufacturerIdentifier;
    }

    public ApplePayDecryptedPaymentDataFixture withDeviceManufacturerIdentifier(String deviceManufacturerIdentifier) {
        this.deviceManufacturerIdentifier = deviceManufacturerIdentifier;
        return this;
    }

    public String getPaymentDataType() {
        return paymentDataType;
    }

    public ApplePayDecryptedPaymentDataFixture withPaymentDataType(String paymentDataType) {
        this.paymentDataType = paymentDataType;
        return this;
    }

    public String getOnlinePaymentCryptogram() {
        return onlinePaymentCryptogram;
    }


    public String getEciIndicator() {
        return eciIndicator;
    }

    public ApplePayDecryptedPaymentDataFixture withEciIndicator(String eciIndicator) {
        this.eciIndicator = eciIndicator;
        return this;
    }

    public AppleDecryptedPaymentData build() {
        return new AppleDecryptedPaymentData(
                applePaymentInfo,
                applicationPrimaryAccountNumber,
                applicationExpirationDate,
                currencyCode,
                transactionAmount,
                deviceManufacturerIdentifier,
                paymentDataType,
                new AppleDecryptedPaymentData.PaymentData(
                        onlinePaymentCryptogram,
                        eciIndicator
                )
        );
    }
}
