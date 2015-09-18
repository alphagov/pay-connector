package uk.gov.pay.connector.model.domain;

public class GatewayAccount {
    private static final String MERCHANT_CODE = "MERCHANTCODE";
    private String username;
    private String password;

    public GatewayAccount(String username, String password) {

        this.username = username;
        this.password = password;
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
