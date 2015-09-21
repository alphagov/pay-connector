package uk.gov.pay.connector.model.domain;

public class GatewayAccount {
    private static final String MERCHANT_CODE = "MERCHANTCODE";
    private String username;
    private String password;

    private GatewayAccount(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public static GatewayAccount gatewayAccountFor(String username, String password) {
        return new GatewayAccount(username, password);
    }

    public String getMerchantId() {
        return MERCHANT_CODE;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
