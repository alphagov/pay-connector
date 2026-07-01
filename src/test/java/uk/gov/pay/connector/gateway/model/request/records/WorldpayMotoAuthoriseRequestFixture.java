package uk.gov.pay.connector.gateway.model.request.records;

public class WorldpayMotoAuthoriseRequestFixture {

    private String cardNumber = "4242424242424242";
    private String expiryDateMonth = "11";
    private String expiryDateYear = "2030";
    private String cardholderName = "Alec Barley";
    private String cvc = "123";
    private String orderCode = "MyUniqueTransactionId";
    private String description = "My description";
    private String username = "username"; // pragma: allowlist secret
    private String password = "password"; // pragma: allowlist secret
    private String merchantCode = "MERCHANTCODE";
    private long amountInPence = 2000L;

    public static WorldpayMotoAuthoriseRequestFixture aWorldpayMotoAuthoriseRequestFixture() {
        return new WorldpayMotoAuthoriseRequestFixture();
    }

    public WorldpayMotoAuthoriseRequestFixture withCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
        return this;
    }

    public WorldpayMotoAuthoriseRequestFixture withExpiryDateMonth(String expiryDateMonth) {
        this.expiryDateMonth = expiryDateMonth;
        return this;
    }

    public WorldpayMotoAuthoriseRequestFixture withExpiryDateYear(String expiryDateYear) {
        this.expiryDateYear = expiryDateYear;
        return this;
    }

    public WorldpayMotoAuthoriseRequestFixture withCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
        return this;
    }

    public WorldpayMotoAuthoriseRequestFixture withCvc(String cvc) {
        this.cvc = cvc;
        return this;
    }

    public WorldpayMotoAuthoriseRequestFixture withOrderCode(String orderCode) {
        this.orderCode = orderCode;
        return this;
    }

    public WorldpayMotoAuthoriseRequestFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public WorldpayMotoAuthoriseRequestFixture withUsername(String username) {
        this.username = username;
        return this;
    }

    public WorldpayMotoAuthoriseRequestFixture withPassword(String password) {
        this.password = password;
        return this;
    }

    public WorldpayMotoAuthoriseRequestFixture withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
        return this;
    }

    public WorldpayMotoAuthoriseRequestFixture withAmountInPence(long amountInPence) {
        this.amountInPence = amountInPence;
        return this;
    }

    public WorldpayMotoAuthoriseRequest build() {
        return new WorldpayMotoAuthoriseRequest(
                username,
                password,
                merchantCode,
                orderCode,
                description,
                String.valueOf(amountInPence),
                cardNumber,
                expiryDateMonth,
                expiryDateYear,
                cardholderName,
                cvc);
    }

}
