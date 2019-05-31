package uk.gov.pay.connector.events;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResourceType {
    PAYMENT;
    
    @JsonValue
    public String getLowercase() {
        return this.name().toLowerCase();
    }

}
