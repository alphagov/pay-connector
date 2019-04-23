package uk.gov.pay.connector.charge.model;

import uk.gov.pay.connector.util.ValidExternalMetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ExternalMetadata {

    @ValidExternalMetadata
    private final Map<String, Object> metadata;

    public ExternalMetadata(Map<String, Object> metadata) {
        this.metadata = new HashMap<>(metadata);
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }
}
