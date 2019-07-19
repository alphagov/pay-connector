package uk.gov.pay.connector.events.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourceType {
    PAYMENT;
    
    @JsonValue
    public String getLowercase() {
        return this.name().toLowerCase();
    }

}
