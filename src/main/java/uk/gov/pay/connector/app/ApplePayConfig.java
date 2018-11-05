package uk.gov.pay.connector.app;


import java.nio.charset.StandardCharsets;

public class ApplePayConfig {
    private String publicCertificate;
    private String privateKey;

    public byte[] getPublicCertificate() {
        return publicCertificate.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] getPrivateKey() {
        return privateKey.getBytes(StandardCharsets.UTF_8);
    }
}
