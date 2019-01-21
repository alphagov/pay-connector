package uk.gov.pay.connector.model.domain.applepay;

import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

public final class ApplePayPaymentInfoFixture {
    String lastDigitsCardNumber = "4242";
    String brand = "visa";
    PayersCardType cardType = PayersCardType.DEBIT;
    String cardholderName = "Mr. Payment";
    String email = "mr@payment.test";
    private ApplePayPaymentInfoFixture() {
    }

    public static ApplePayPaymentInfoFixture anApplePayPaymentInfo() {
        return new ApplePayPaymentInfoFixture();
    }

    public String getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public ApplePayPaymentInfoFixture withLastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        return this;
    }

    public String getBrand() {
        return brand;
    }

    public PayersCardType getCardType() {
        return cardType;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public ApplePayPaymentInfoFixture withCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public ApplePayPaymentInfoFixture withEmail(String email) {
        this.email = email;
        return this;
    }

    public WalletPaymentInfo build() {
        return new WalletPaymentInfo(
                lastDigitsCardNumber,
                brand,
                cardType,
                cardholderName,
                email
        );
    }
}
