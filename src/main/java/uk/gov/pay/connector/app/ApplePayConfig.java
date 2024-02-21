package uk.gov.pay.connector.app;

import io.dropwizard.core.Configuration;

import javax.validation.constraints.NotNull;
import java.util.Optional;

import static java.util.function.Predicate.not;

public class ApplePayConfig extends Configuration {

    @NotNull
    private String primaryPrivateKey;

    @NotNull
    private String primaryPublicCertificate;

    private String secondaryPrivateKey;

    private String secondaryPublicCertificate;

    public Optional<String> getSecondaryPublicCertificate() {
        return Optional.ofNullable(secondaryPublicCertificate).filter(not(String::isBlank));
    }

    public Optional<String> getSecondaryPrivateKey() {
        return Optional.ofNullable(secondaryPrivateKey).filter(not(String::isBlank));
    }

    public String getPrimaryPrivateKey() {
        return primaryPrivateKey;
    }

    public String getPrimaryPublicCertificate() {
        return primaryPublicCertificate;
    }
}
