package uk.gov.pay.connector.app;


import io.dropwizard.Configuration;

public class ApplePayConfig extends Configuration {
    private String publicCertificate;
    private String privateKey;

    public String getPublicCertificate() {
        return publicCertificate;
    }

    public String getPrivateKey() {
        return privateKey;
    }
}
