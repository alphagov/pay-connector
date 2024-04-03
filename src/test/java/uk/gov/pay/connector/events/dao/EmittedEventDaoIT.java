package uk.gov.pay.connector.events.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.events.EmittedEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.refund.RefundSubmitted;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class EmittedEventDaoIT {
    @RegisterExtension
    static ITestBaseExtension app = new ITestBaseExtension("sandbox");
    private EmittedEventDao emittedEventDao;

    @BeforeEach
    void setUp() {
        emittedEventDao = app.getInstanceFromGuiceContainer(EmittedEventDao.class);
    }

    @Test
    void persistEmittedEvent_succeeds() {
        final Instant eventDate = Instant.parse("2019-01-01T11:00:00Z");

        EmittedEventEntity emittedEvent = new EmittedEventEntity(
                "resource-type",
                "external-id",
                "event-type",
                eventDate,
                Instant.parse("2019-01-01T13:00:00Z"),
                ZonedDateTime.parse("2019-01-02T13:00:00Z")
        );

        emittedEventDao.persist(emittedEvent);
        assertThat(emittedEvent.getId(), is(notNullValue()));

        final Map<String, Object> persisted = app.getDatabaseTestHelper().readEmittedEvent(emittedEvent.getId());

        assertThat(persisted.get("resource_type"), is("resource-type"));
        assertThat(persisted.get("resource_external_id"), is("external-id"));
        assertThat(persisted.get("event_type"), is("event-type"));
        final Timestamp eventDateInUTC = Timestamp.valueOf("2019-01-01 11:00:00");
        assertThat(persisted.get("event_date"), is(eventDateInUTC));
        assertThat(persisted.get("emitted_date"), is(Timestamp.valueOf("2019-01-01 13:00:00")));
        assertThat(persisted.get("do_not_retry_emit_until"), is(Timestamp.valueOf("2019-01-02 13:00:00")));
    }

    @Test
    void recordEmission_shouldRecordEmission() {
        final PaymentCreated eventThatHasBeenEmitted = aPaymentCreatedEvent();
        ZonedDateTime doNotRetryEmitUntilDate = ZonedDateTime.parse("2019-01-02T13:00:00Z");
        emittedEventDao.recordEmission(eventThatHasBeenEmitted, doNotRetryEmitUntilDate);

        final List<Map<String, Object>> events = app.getDatabaseTestHelper().readEmittedEvents();

        assertThat(events.size(), is(1));
        final Map<String, Object> event = events.get(0);
        assertThat(event.get("resource_external_id"), is(eventThatHasBeenEmitted.getResourceExternalId()));
        assertThat(event.get("do_not_retry_emit_until"), is(Timestamp.from(doNotRetryEmitUntilDate.toInstant())));
    }

    @Test
    void hasBeenEmittedBefore_shouldReturnFalseIfNoRecord() {
        final boolean b = emittedEventDao.hasBeenEmittedBefore(aPaymentCreatedEvent());

        final List<Map<String, Object>> events = app.getDatabaseTestHelper().readEmittedEvents();
        assertThat(events.size(), is(0));

        assertThat(b, is(false));
    }

    @Test
    void hasBeenEmittedBefore_shouldReturnTrueIfRecordedBefore() {
        final PaymentCreated paymentCreatedEvent = aPaymentCreatedEvent();

        emittedEventDao.recordEmission(paymentCreatedEvent, null);

        assertThat(emittedEventDao.hasBeenEmittedBefore(paymentCreatedEvent), is(true));
    }

    @Test
    void recordEmissionWithParameters_shouldRecordEmission() {
        final PaymentCreated eventToRecord = aPaymentCreatedEvent();
        ZonedDateTime doNotRetryEmitUntilDate = ZonedDateTime.parse("2019-01-02T13:00:00Z");
        emittedEventDao.recordEmission(eventToRecord.getResourceType(), eventToRecord.getResourceExternalId(),
                eventToRecord.getEventType(), eventToRecord.getTimestamp(), doNotRetryEmitUntilDate);

        final List<Map<String, Object>> events = app.getDatabaseTestHelper().readEmittedEvents();

        assertThat(events.size(), is(1));
        final Map<String, Object> event = events.get(0);
        assertThat(event.get("resource_external_id"), is(eventToRecord.getResourceExternalId()));
        assertThat(event.get("resource_type"), is(eventToRecord.getResourceType().getLowercase()));
        assertThat(event.get("event_type"), is(eventToRecord.getEventType()));
        assertThat(event.get("emitted_date"), is(nullValue()));
        assertThat(event.get("do_not_retry_emit_until"), is(Timestamp.from(doNotRetryEmitUntilDate.toInstant())));
    }

    @Test
    void markEventAsEmitted_shouldRecordEventAndEmittedDate() {
        final RefundSubmitted eventToRecord = aRefundSubmittedEvent(Instant.parse("2018-01-01T12:00:00Z"));

        emittedEventDao.recordEmission(eventToRecord.getResourceType(), eventToRecord.getResourceExternalId(),
                eventToRecord.getEventType(), eventToRecord.getTimestamp(), null);

        List<Map<String, Object>> events = app.getDatabaseTestHelper().readEmittedEvents();
        Map<String, Object> event = events.get(0);
        assertThat(event.get("emitted_date"), is(nullValue()));
        assertThat(event.get("event_date").toString(), is("2018-01-01 12:00:00.0"));

        final RefundSubmitted eventToUpdate = aRefundSubmittedEvent(Instant.parse("2019-01-01T14:00:00Z"));
        emittedEventDao.markEventAsEmitted(eventToUpdate);
        events = app.getDatabaseTestHelper().readEmittedEvents();
        event = events.get(0);
        assertThat(event.get("emitted_date"), is(notNullValue()));
        assertThat(event.get("event_date").toString(), is("2019-01-01 14:00:00.0"));
    }

    @Test
    void markEventAsEmitted_shouldNotUpdateIfNoRecordIsFoundWithNullEmittedDate() {
        final PaymentCreated eventToRecord = aPaymentCreatedEvent();
        emittedEventDao.recordEmission(eventToRecord, null);

        List<Map<String, Object>> events = app.getDatabaseTestHelper().readEmittedEvents();

        Map<String, Object> event = events.get(0);
        assertThat(event.get("emitted_date"), is(notNullValue()));
        String emittedDateBeforeUpdate = event.get("emitted_date").toString();

        emittedEventDao.markEventAsEmitted(eventToRecord);
        events = app.getDatabaseTestHelper().readEmittedEvents();
        event = events.get(0);
        assertThat(event.get("emitted_date").toString(), is(emittedDateBeforeUpdate));
    }

    @Test
    void findNotEmittedEventsOlderThan_shouldReturnEventsWithEmptyEmittedDate() {
        final PaymentCreated paymentCreatedEvent = aPaymentCreatedEvent();
        emittedEventDao.recordEmission(paymentCreatedEvent.getResourceType(), paymentCreatedEvent.getResourceExternalId(),
                paymentCreatedEvent.getEventType(), paymentCreatedEvent.getTimestamp(), null);

        Optional<Long> maxId = emittedEventDao.findNotEmittedEventMaxIdOlderThan(Instant.parse("2019-01-01T14:00:01Z"),
                ZonedDateTime.now(UTC));
        assertThat(maxId.isPresent(), is(true));

        List<EmittedEventEntity> notEmittedEvents = emittedEventDao.findNotEmittedEventsOlderThan(
                Instant.parse("2019-01-01T14:00:01Z"), 1, 0L, Long.MAX_VALUE,  ZonedDateTime.now(UTC));

        assertThat(notEmittedEvents.size(), is(1));
        assertThat(notEmittedEvents.get(0).getEmittedDate(), nullValue());
        assertThat(notEmittedEvents.get(0).getEventType(), is(paymentCreatedEvent.getEventType()));
        assertThat(notEmittedEvents.get(0).getResourceExternalId(), is(paymentCreatedEvent.getResourceExternalId()));
        assertThat(notEmittedEvents.get(0).getResourceType(), is(paymentCreatedEvent.getResourceType().getLowercase()));
    }

    @Test
    void findNotEmittedEventsOlderThan_shouldNotReturnRecordsWithDoNotRetryEmitUntilValueInFuture() {
        final PaymentCreated paymentCreatedEvent = aPaymentCreatedEvent();
        final RefundSubmitted refundSubmittedEvent = aRefundSubmittedEvent(Instant.parse("2019-01-01T14:00:00Z"));
        emittedEventDao.recordEmission(paymentCreatedEvent.getResourceType(), paymentCreatedEvent.getResourceExternalId(),
                paymentCreatedEvent.getEventType(), paymentCreatedEvent.getTimestamp(), null);
        ZonedDateTime doNotRetryEmitUntil = ZonedDateTime.now(UTC).minusSeconds(120);
        emittedEventDao.recordEmission(refundSubmittedEvent.getResourceType(), refundSubmittedEvent.getResourceExternalId(),
                refundSubmittedEvent.getEventType(), refundSubmittedEvent.getTimestamp(), doNotRetryEmitUntil);
        emittedEventDao.recordEmission(paymentCreatedEvent.getResourceType(), paymentCreatedEvent.getResourceExternalId(),
                paymentCreatedEvent.getEventType(), paymentCreatedEvent.getTimestamp(), ZonedDateTime.now(UTC).plusSeconds(120));

        Optional<Long> maxId = emittedEventDao.findNotEmittedEventMaxIdOlderThan(Instant.parse("2019-01-01T14:00:01Z"), ZonedDateTime.now(UTC));
        assertThat(maxId.isPresent(), is(true));

        List<EmittedEventEntity> notEmittedEvents = emittedEventDao.findNotEmittedEventsOlderThan(
                Instant.parse("2019-01-01T14:00:01Z"), 2, 0L, Long.MAX_VALUE, ZonedDateTime.now(UTC));

        assertThat(notEmittedEvents.size(), is(2));
        assertThat(notEmittedEvents.get(0).getEmittedDate(), nullValue());
        assertThat(notEmittedEvents.get(0).getEventType(), is(paymentCreatedEvent.getEventType()));
        assertThat(notEmittedEvents.get(0).getResourceExternalId(), is(paymentCreatedEvent.getResourceExternalId()));
        assertThat(notEmittedEvents.get(0).getResourceType(), is(paymentCreatedEvent.getResourceType().getLowercase()));
        assertThat(notEmittedEvents.get(0).getDoNotRetryEmitUntil(), is(nullValue()));

        assertThat(notEmittedEvents.get(1).getEmittedDate(), nullValue());
        assertThat(notEmittedEvents.get(1).getEventType(), is(refundSubmittedEvent.getEventType()));
        assertThat(notEmittedEvents.get(1).getResourceExternalId(), is(refundSubmittedEvent.getResourceExternalId()));
        assertThat(notEmittedEvents.get(1).getResourceType(), is(refundSubmittedEvent.getResourceType().getLowercase()));
        assertThat(Timestamp.from(notEmittedEvents.get(1).getDoNotRetryEmitUntil().toInstant()), 
                is(Timestamp.from(doNotRetryEmitUntil.toInstant())));
    }

    private PaymentCreated aPaymentCreatedEvent() {
        PaymentCreatedEventDetails eventDetails = new PaymentCreatedEventDetails.Builder()
                .withAmount(1L)
                .withDescription("desc")
                .withReference("ref")
                .withReturnUrl("return_url")
                .withGatewayAccountId(100L)
                .withPaymentProvider("someProvider")
                .withLanguage("en")
                .withDelayedCapture(false)
                .withLive(false)
                .build();
        return new PaymentCreated("service-id", true, 100L, "my-resource-external-id", eventDetails,
                Instant.parse("2019-01-01T14:00:00Z"));
    }

    private RefundSubmitted aRefundSubmittedEvent(Instant eventTimestamp) {
        return new RefundSubmitted("service-id", true, 100L, "my-resource-external-id",
                "parent-external-id",
                null, eventTimestamp);
    }
}
