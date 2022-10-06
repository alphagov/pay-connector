package uk.gov.pay.connector.events;

import java.time.Instant;

public class EmittedEventFixture {
    private Long id = 1L;
    private String resourceType = "payment";
    private String resourceExternalId = "some-external-id";
    private String eventType = "PAYMENT_CREATED";
    private Instant eventDate = Instant.parse("2019-09-20T10:00:00Z");
    private Instant emittedDate;

    public static EmittedEventFixture anEmittedEventEntity() {
        return new EmittedEventFixture();
    }

    public EmittedEventEntity build() {
        var event = new EmittedEventEntity(resourceType, resourceExternalId, eventType,
                eventDate, emittedDate, null);
        event.setId(id);

        return event;
    }

    public EmittedEventFixture withEmittedDate(Instant emittedDate) {
        this.emittedDate = emittedDate;
        return this;
    }

    public EmittedEventFixture withEventDate(Instant eventDate) {
        this.eventDate = eventDate;
        return this;
    }

    public EmittedEventFixture withEventType(String eventType) {
        this.eventType = eventType;
        return this;
    }

    public EmittedEventFixture withResourceExternalId(String resourceExternalId) {
        this.resourceExternalId = resourceExternalId;
        return this;
    }

    public EmittedEventFixture withResourceType(String resourceType) {
        this.resourceType = resourceType;
        return this;
    }

    public EmittedEventFixture withId(Long id) {
        this.id = id;
        return this;
    }
}
