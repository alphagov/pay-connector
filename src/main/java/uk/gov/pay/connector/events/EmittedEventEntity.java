package uk.gov.pay.connector.events;

import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
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

    @Convert(converter = UTCDateTimeConverter.class)
    @Column(name = "event_date")
    private ZonedDateTime eventDate;

    @Convert(converter = UTCDateTimeConverter.class)
    @Column(name = "emitted_date")
    private ZonedDateTime emittedDate;

    @Convert(converter = UTCDateTimeConverter.class)
    @Column(name = "do_not_retry_emit_until")
    private ZonedDateTime doNotRetryEmitUntil;

    protected EmittedEventEntity() {
    }

    public EmittedEventEntity(String resourceType, String resourceExternalId, String eventType, 
                              ZonedDateTime eventDate, ZonedDateTime emittedDate,
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

    public ZonedDateTime getEventDate() {
        return eventDate;
    }

    public ZonedDateTime getEmittedDate() {
        return emittedDate;
    }

    public ZonedDateTime getDoNotRetryEmitUntil() {
        return doNotRetryEmitUntil;
    }

    public void setEmittedDate(ZonedDateTime emittedDate) {
        this.emittedDate = emittedDate;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
