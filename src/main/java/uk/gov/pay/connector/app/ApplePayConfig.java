package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;

import javax.validation.constraints.NotNull;
import java.util.Optional;

public class ApplePayConfig extends Configuration {

    @NotNull
    private String primaryPrivateKey;

    @NotNull
    private String primaryPublicCertificate;

    private String secondaryPrivateKey;

    private String secondaryPublicCertificate;

    public Optional<String> getSecondaryPublicCertificate() {
        return Optional.ofNullable(secondaryPublicCertificate);
    }

    public Optional<String> getSecondaryPrivateKey() {
        return Optional.ofNullable(secondaryPrivateKey);
    }

    public String getPrimaryPrivateKey() {
        return primaryPrivateKey;
    }

    public String getPrimaryPublicCertificate() {
        return primaryPublicCertificate;
    }
}
