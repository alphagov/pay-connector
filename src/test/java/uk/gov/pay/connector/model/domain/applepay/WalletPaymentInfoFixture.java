package uk.gov.pay.connector.model.domain.applepay;

import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

public final class WalletPaymentInfoFixture {

    private String lastDigitsCardNumber = "4242";
    private String brand = "visa";
    private PayersCardType cardType = PayersCardType.DEBIT;
    String cardholderName= "Mr. Payment";
    String email = "mr@payment.test";
    private String acceptHeader;
    private String userAgentHeader;
    private String ipAddress;
    private String displayName;
    private String network;
    private String transactionIdentifier;
    private String worldpay3dsFlexDdcResult;
    
    private WalletPaymentInfoFixture() {
    }

    public static WalletPaymentInfoFixture aWalletPaymentInfo() {
        return new WalletPaymentInfoFixture();
    }

    public WalletPaymentInfoFixture withLastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        return this;
    }

    public WalletPaymentInfoFixture withBrand(String brand) {
        this.brand = brand;
        return this;
    }

    public WalletPaymentInfoFixture withCardType(PayersCardType cardType) {
        this.cardType = cardType;
        return this;
    }

    public WalletPaymentInfoFixture withCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
        return this;
    }

    public WalletPaymentInfoFixture withEmail(String email) {
        this.email = email;
        return this;
    }

    public WalletPaymentInfoFixture withAcceptHeader(String acceptHeader) {
        this.acceptHeader = acceptHeader;
        return this;
    }

    public WalletPaymentInfoFixture withUserAgentHeader(String userAgentHeader) {
        this.userAgentHeader = userAgentHeader;
        return this;
    }

    public WalletPaymentInfoFixture withIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public WalletPaymentInfoFixture withDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public WalletPaymentInfoFixture withNetwork(String network) {
        this.network = network;
        return this;
    }

    public WalletPaymentInfoFixture withTransactionIdentifier(String transactionIdentifier) {
        this.transactionIdentifier = transactionIdentifier;
        return this;
    }

    public WalletPaymentInfoFixture withWorldpay3dsFlexDdcResult(String worldpay3dsFlexDdcResult) {
        this.worldpay3dsFlexDdcResult = worldpay3dsFlexDdcResult;
        return this;
    }

    public WalletPaymentInfo build() {
        return new WalletPaymentInfo(lastDigitsCardNumber, brand, cardType, cardholderName, email, acceptHeader, userAgentHeader, ipAddress, displayName, network, transactionIdentifier, worldpay3dsFlexDdcResult);
    }
}
