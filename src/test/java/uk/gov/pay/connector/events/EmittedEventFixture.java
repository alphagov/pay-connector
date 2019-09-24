package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

public class EmittedEventFixture {
    private String resourceType = "payment";
    private String resourceExternalId = "some-external-id";
    private String eventType = "PaymentCreated";
    private ZonedDateTime eventDate = ZonedDateTime.parse("2019-09-20T10:00Z");
    private ZonedDateTime emittedDate;

    public static EmittedEventFixture anEmittedEventEntity() {
        return new EmittedEventFixture();
    }

    public EmittedEventEntity build() {
        return new EmittedEventEntity(resourceType, resourceExternalId, eventType,
                eventDate, emittedDate);
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
}
