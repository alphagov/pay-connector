package uk.gov.pay.connector.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.dropwizard.jackson.Jackson;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Event {
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();

    public abstract ResourceType getResourceType();
    public abstract String getResourceExternalId();
    public abstract String getEventType();
    public abstract EventDetails getEventDetails();
    @JsonSerialize(using = MicrosecondPrecisionDateTimeSerializer.class)
    public abstract ZonedDateTime getTimestamp();
    
    public String toJsonString() throws JsonProcessingException {
        return MAPPER.writeValueAsString(this);
    }
}
