package uk.gov.pay.connector.app;


import io.dropwizard.Configuration;

import java.nio.charset.StandardCharsets;

public class ApplePayConfig extends Configuration {
    private String publicCertificate;
    private String privateKey;

    public byte[] getPublicCertificate() {
        return publicCertificate.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] getPrivateKey() {
        return privateKey.getBytes(StandardCharsets.UTF_8);
    }
}
