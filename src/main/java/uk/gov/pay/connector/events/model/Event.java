package uk.gov.pay.connector.events.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.dropwizard.jackson.Jackson;
import uk.gov.pay.connector.events.eventdetails.EmptyEventDetails;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.service.payments.commons.api.json.ApiResponseInstantWithMicrosecondPrecisionSerializer;

import java.time.Instant;
import java.util.Objects;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class Event {
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
    private String resourceExternalId;
    private EventDetails eventDetails;
    private Instant timestamp;

    public Event(Instant timestamp, String resourceExternalId, EventDetails eventDetails) {
        this.timestamp = timestamp;
        this.resourceExternalId = resourceExternalId;
        this.eventDetails = eventDetails;
    }

    public Event(Instant timestamp, String resourceExternalId) {
        this(timestamp, resourceExternalId, new EmptyEventDetails());
    }

    public abstract ResourceType getResourceType();

    public String getResourceExternalId() {
        return resourceExternalId;
    }

    public EventDetails getEventDetails() {
        return eventDetails;
    }

    @JsonSerialize(using = ApiResponseInstantWithMicrosecondPrecisionSerializer.class)
    public Instant getTimestamp() {
        return timestamp;
    }

    public String toJsonString() throws JsonProcessingException {
        return MAPPER.writeValueAsString(this);
    }

    public String getEventType() {
        return eventTypeForClass(this.getClass());
    }

    public static String eventTypeForClass(Class clazz) {
        return clazz.getSimpleName().replaceAll("([^A-Z0-9]+)([A-Z0-9])", "$1_$2").toUpperCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event that = (Event) o;
        return Objects.equals(resourceExternalId, that.resourceExternalId) &&
                Objects.equals(eventDetails, that.eventDetails) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceExternalId, eventDetails, timestamp);
    }

    @Override
    public String toString() {
        return "Event{" +
                "eventType=" + getEventType() +
                ", resourceExternalId='" + resourceExternalId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
