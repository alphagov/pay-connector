package uk.gov.pay.connector.gateway.model.request.records;

public class WorldpayMotoAuthorisePayloadFixture {

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

    public static WorldpayMotoAuthorisePayloadFixture aWorldpayMotoAuthorisePayloadFixture() {
        return new WorldpayMotoAuthorisePayloadFixture();
    }

    public WorldpayMotoAuthorisePayloadFixture withCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
        return this;
    }

    public WorldpayMotoAuthorisePayloadFixture withExpiryDateMonth(String expiryDateMonth) {
        this.expiryDateMonth = expiryDateMonth;
        return this;
    }

    public WorldpayMotoAuthorisePayloadFixture withExpiryDateYear(String expiryDateYear) {
        this.expiryDateYear = expiryDateYear;
        return this;
    }

    public WorldpayMotoAuthorisePayloadFixture withCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
        return this;
    }

    public WorldpayMotoAuthorisePayloadFixture withCvc(String cvc) {
        this.cvc = cvc;
        return this;
    }

    public WorldpayMotoAuthorisePayloadFixture withOrderCode(String orderCode) {
        this.orderCode = orderCode;
        return this;
    }

    public WorldpayMotoAuthorisePayloadFixture withDescription(String description) {
        this.description = description;
        return this;
    }

    public WorldpayMotoAuthorisePayloadFixture withUsername(String username) {
        this.username = username;
        return this;
    }

    public WorldpayMotoAuthorisePayloadFixture withPassword(String password) {
        this.password = password;
        return this;
    }

    public WorldpayMotoAuthorisePayloadFixture withMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
        return this;
    }

    public WorldpayMotoAuthorisePayloadFixture withAmountInPence(long amountInPence) {
        this.amountInPence = amountInPence;
        return this;
    }

    public WorldpayMotoAuthorisePayload build() {
        return new WorldpayMotoAuthorisePayload(
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
