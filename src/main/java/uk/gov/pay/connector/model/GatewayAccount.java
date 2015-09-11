package uk.gov.pay.connector.model;

public class GatewayAccount {
    private static final String MERCHANT_CODE = "MERCHANTCODE";
    private static final String XML_USERNAME = "MERCHANTCODE";
    private static final String XML_PASSWORD = "***";
    private String principal;
    private String credential;

    public GatewayAccount() {
        this(XML_USERNAME, XML_PASSWORD);
    }

    public GatewayAccount(String principal, String credential) {

        this.principal = principal;
        this.credential = credential;
    }

    public String getMerchantId() {
        return MERCHANT_CODE;
    }

    public String getGatewayPrincipal() {
        return principal;
    }

    public String getGatewayPassword() {
        return credential;
    }
}
