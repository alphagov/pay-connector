package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class EmittedEventFixture {
    private Long id = 1L;
    private String resourceType = "payment";
    private String resourceExternalId = "some-external-id";
    private String eventType = "PAYMENT_CREATED";
    private ZonedDateTime eventDate = ZonedDateTime.parse("2019-09-20T10:00Z");
    private ZonedDateTime emittedDate;

    public static EmittedEventFixture anEmittedEventEntity() {
        return new EmittedEventFixture();
    }

    public EmittedEventEntity build() {
        var event = new EmittedEventEntity(resourceType, resourceExternalId, eventType,
                eventDate, emittedDate);
        event.setId(id);

        return event;
    }

    public EmittedEventFixture withEmittedDate(ZonedDateTime emittedDate) {
        this.emittedDate = emittedDate;
        return this;
    }

    public EmittedEventFixture withEventDate(ZonedDateTime eventDate) {
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
