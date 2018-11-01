package uk.gov.pay.connector.app;


public class ApplePayConfig extends GatewayConfig {
    private String publicCertificate;
    private String privateKey;

    public byte[] getPublicCertificate() {
        return publicCertificate.getBytes();
    }

    public byte[] getPrivateKey() {
        return privateKey.getBytes();
    }
}
