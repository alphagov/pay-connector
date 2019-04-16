package uk.gov.pay.connector.charge.model;

import java.util.Map;

public class ExternalMetadata {

    private final Map<String, Object> metadata;

    public ExternalMetadata(Map<String, Object> metadata) {
        this.metadata = Map.copyOf(metadata);
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
