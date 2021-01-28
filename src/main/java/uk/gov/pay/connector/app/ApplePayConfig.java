package uk.gov.pay.connector.app;

import io.dropwizard.Configuration;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ApplePayConfig extends Configuration {

    @NotNull
    private String primaryPrivateKey;

    @NotNull
    private String primaryPublicCertificate;

    private String secondaryPrivateKey;

    private String secondaryPublicCertificate;

    public Optional<String> getSecondaryPublicCertificate() {
        if (isNotBlank(secondaryPublicCertificate)) {
            return Optional.of(secondaryPublicCertificate);
        } else {
            return Optional.empty();
        }
    }

    public Optional<String> getSecondaryPrivateKey() {
        if (isNotBlank(secondaryPrivateKey)) {
            return Optional.of(secondaryPrivateKey);
        } else {
            return Optional.empty();
        }
    }

    public String getPrimaryPrivateKey() {
        return primaryPrivateKey;
    }

    public String getPrimaryPublicCertificate() {
        return primaryPublicCertificate;
    }
}
