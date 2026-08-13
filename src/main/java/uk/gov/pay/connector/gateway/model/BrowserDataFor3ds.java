package uk.gov.pay.connector.gateway.model;

import java.util.Optional;

public interface BrowserDataFor3ds {

    default Optional<String> getBrowserAcceptHeader() {
        return Optional.empty();
    }

    default Optional<String> getBrowserColorDepth() {
        return Optional.empty();
    }

    default Optional<Boolean> getBrowserJavaScriptEnabled() {
        return Optional.empty();
    }

    default Optional<String> getBrowserLanguage() {
        return Optional.empty();
    }

    default Optional<String> getBrowserScreenHeight() {
        return Optional.empty();
    }

    default Optional<String> getBrowserScreenWidth() {
        return Optional.empty();
    }

    default Optional<String> getBrowserTZ() {
        return Optional.empty();
    }

    default Optional<String> getBrowserUserAgent() {
        return Optional.empty();
    }

    default Optional<String> getBrowserIpAddress() {
        return Optional.empty();
    }
}
