package uk.gov.pay.connector.app.adyen;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

public class WebhookHmacKeys {

    private final String primary;
    private final String secondary;

    @JsonCreator
    public WebhookHmacKeys(@JsonProperty("primary") String primary,
                           @JsonProperty("secondary") String secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    public Optional<String> getPrimary() {
        return Optional.ofNullable(primary).filter(value -> !value.isBlank());
    }

    public Optional<String> getSecondary() {
        return Optional.ofNullable(secondary).filter(value -> !value.isBlank());
    }
}
