package uk.gov.pay.connector.events;

import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.service.payments.commons.jpa.InstantToUtcTimestampWithoutTimeZoneConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZonedDateTime;

@Entity
@Table(name = "emitted_events")
@SequenceGenerator(name = "emitted_events_id_seq",
        sequenceName = "emitted_events_id_seq", allocationSize = 1)
public class EmittedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "emitted_events_id_seq")
    private Long id;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_external_id")
    private String resourceExternalId;

    @Column(name = "event_type")
    private String eventType;

    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    @Column(name = "event_date")
    private Instant eventDate;

    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    @Column(name = "emitted_date")
    private Instant emittedDate;

    @Convert(converter = UTCDateTimeConverter.class)
    @Column(name = "do_not_retry_emit_until")
    private ZonedDateTime doNotRetryEmitUntil;

    protected EmittedEventEntity() {
    }

    public EmittedEventEntity(String resourceType, String resourceExternalId, String eventType,
                              Instant eventDate, Instant emittedDate,
                              ZonedDateTime doNotRetryEmitUntil) {
        this.resourceType = resourceType;
        this.resourceExternalId = resourceExternalId;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.emittedDate = emittedDate;
        this.doNotRetryEmitUntil = doNotRetryEmitUntil;
    }

    public Long getId() {
        return id;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceExternalId() {
        return resourceExternalId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getEventDate() {
        return eventDate;
    }

    public Instant getEmittedDate() {
        return emittedDate;
    }

    public ZonedDateTime getDoNotRetryEmitUntil() {
        return doNotRetryEmitUntil;
    }

    public void setEmittedDate(Instant emittedDate) {
        this.emittedDate = emittedDate;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setDoNotRetryEmitUntil(ZonedDateTime doNotRetryEmitUntil) {
        this.doNotRetryEmitUntil = doNotRetryEmitUntil;
    }

    @Override
    public String toString() {
        return "EmittedEventEntity{" +
                "id=" + id +
                ", resourceType='" + resourceType + '\'' +
                ", resourceExternalId='" + resourceExternalId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", eventDate=" + eventDate +
                ", emittedDate=" + emittedDate +
                ", doNotRetryEmitUntil=" + doNotRetryEmitUntil +
                '}';
    }
}
