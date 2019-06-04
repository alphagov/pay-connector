package uk.gov.pay.connector.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.jackson.Jackson;

public abstract class Event {
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

    public abstract ResourceType getResourceType();

    public abstract String getEventType();

    public String toJsonString() throws JsonProcessingException {
        return MAPPER.writeValueAsString(this);
    }
}
