package uk.gov.pay.connector.model.domain.applepay;

import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayPaymentInfo;

public final class ApplePayPaymentInfoFixture {

    private String lastDigitsCardNumber = "4242";
    private String brand = "visa";
    private PayersCardType cardType = PayersCardType.DEBIT;
    String cardholderName = "Mr. Payment";
    String email = "mr@payment.test";
    private String displayName;
    private String network;
    private String transactionIdentifier;

    private ApplePayPaymentInfoFixture() {
    }

    public static ApplePayPaymentInfoFixture anApplePayPaymentInfo() {
        return new ApplePayPaymentInfoFixture();
    }

    public ApplePayPaymentInfoFixture withLastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        return this;
    }

    public ApplePayPaymentInfoFixture withBrand(String brand) {
        this.brand = brand;
        return this;
    }

    public ApplePayPaymentInfoFixture withCardType(PayersCardType cardType) {
        this.cardType = cardType;
        return this;
    }

    public ApplePayPaymentInfoFixture withCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
        return this;
    }

    public ApplePayPaymentInfoFixture withEmail(String email) {
        this.email = email;
        return this;
    }

    public ApplePayPaymentInfoFixture withDisplayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    public ApplePayPaymentInfoFixture withNetwork(String network) {
        this.network = network;
        return this;
    }

    public ApplePayPaymentInfoFixture withTransactionIdentifier(String transactionIdentifier) {
        this.transactionIdentifier = transactionIdentifier;
        return this;
    }

    public ApplePayPaymentInfo build() {
        return new ApplePayPaymentInfo(lastDigitsCardNumber, brand, cardType, cardholderName, email, displayName, network, transactionIdentifier);
    }
}
