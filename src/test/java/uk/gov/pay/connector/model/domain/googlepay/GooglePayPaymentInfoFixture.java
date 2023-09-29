package uk.gov.pay.connector.model.domain.googlepay;

import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayPaymentInfo;

public final class GooglePayPaymentInfoFixture {

    private String lastDigitsCardNumber = "4242";
    private String brand = "visa";
    private PayersCardType cardType = PayersCardType.DEBIT;
    String cardholderName= "Mr. Payment";
    String email = "mr@payment.test";
    private String acceptHeader;
    private String userAgentHeader;
    private String ipAddress;
    private String worldpay3dsFlexDdcResult;
    
    private GooglePayPaymentInfoFixture() {
    }

    public static GooglePayPaymentInfoFixture aGooglePayPaymentInfo() {
        return new GooglePayPaymentInfoFixture();
    }

    public GooglePayPaymentInfoFixture withLastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        return this;
    }

    public GooglePayPaymentInfoFixture withBrand(String brand) {
        this.brand = brand;
        return this;
    }

    public GooglePayPaymentInfoFixture withCardType(PayersCardType cardType) {
        this.cardType = cardType;
        return this;
    }

    public GooglePayPaymentInfoFixture withCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
        return this;
    }

    public GooglePayPaymentInfoFixture withEmail(String email) {
        this.email = email;
        return this;
    }

    public GooglePayPaymentInfoFixture withAcceptHeader(String acceptHeader) {
        this.acceptHeader = acceptHeader;
        return this;
    }

    public GooglePayPaymentInfoFixture withUserAgentHeader(String userAgentHeader) {
        this.userAgentHeader = userAgentHeader;
        return this;
    }

    public GooglePayPaymentInfoFixture withIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public GooglePayPaymentInfoFixture withWorldpay3dsFlexDdcResult(String worldpay3dsFlexDdcResult) {
        this.worldpay3dsFlexDdcResult = worldpay3dsFlexDdcResult;
        return this;
    }

    public GooglePayPaymentInfo build() {
        return new GooglePayPaymentInfo(lastDigitsCardNumber, brand, cardType, cardholderName, email, acceptHeader, userAgentHeader, ipAddress, worldpay3dsFlexDdcResult);
    }
}
