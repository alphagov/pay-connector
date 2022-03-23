package uk.gov.pay.connector.charge.model;

import java.util.Arrays;
import java.util.Optional;

public enum AuthMode {
    WEB,
    API;
    
    public static Optional<AuthMode> from(String authModeName) {
        return Arrays.stream(AuthMode.values())
                .filter(v -> v.name().equals(authModeName))
                .findFirst();
    }
}
