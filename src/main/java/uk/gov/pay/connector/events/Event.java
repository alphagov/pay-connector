package uk.gov.pay.connector.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.dropwizard.jackson.Jackson;
import uk.gov.pay.connector.events.eventpayload.EventPayload;

import java.time.ZonedDateTime;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Event<T extends EventPayload> {
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

    public abstract ResourceType getResourceType();
    public abstract String getResourceExternalId();
    public abstract String getEventType();
    public abstract T getEventData();
    @JsonSerialize(using = MicrosecondPrecisionDateTimeSerializer.class)
    public abstract ZonedDateTime getEventDate();
    
    String toJsonString() throws JsonProcessingException {
        return MAPPER.writeValueAsString(this);
    }
}
