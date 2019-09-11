package uk.gov.pay.connector.events.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.events.EmittedEventEntity;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.refund.RefundSubmitted;
import uk.gov.pay.connector.it.dao.DaoITestBase;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

public class EmittedEventDaoIT extends DaoITestBase {
    private EmittedEventDao emittedEventDao;

    @Before
    public void setUp() {
        emittedEventDao = env.getInstance(EmittedEventDao.class);
        databaseTestHelper.truncateAllData();
    }

    @Test
    public void persistEmittedEvent_succeeds() {
        final ZonedDateTime eventDate = ZonedDateTime.parse("2019-01-01T12:00:00+01:00");

        EmittedEventEntity emittedEvent = new EmittedEventEntity(
                "resource-type",
                "external-id",
                "event-type",
                eventDate,
                ZonedDateTime.parse("2019-01-01T13:00:00Z")
        );

        emittedEventDao.persist(emittedEvent);
        assertThat(emittedEvent.getId(), is(notNullValue()));

        final Map<String, Object> persisted = databaseTestHelper.readEmittedEvent(emittedEvent.getId());

        assertThat(persisted.get("resource_type"), is("resource-type"));
        assertThat(persisted.get("resource_external_id"), is("external-id"));
        assertThat(persisted.get("event_type"), is("event-type"));
        final Timestamp eventDateInUTC = Timestamp.valueOf("2019-01-01 11:00:00");
        assertThat(persisted.get("event_date"), is(eventDateInUTC));
        assertThat(persisted.get("emitted_date"), is(Timestamp.valueOf("2019-01-01 13:00:00")));
    }

    @Test
    public void recordEmission_shouldRecordEmission() {
        final PaymentCreated eventThatHasBeenEmitted = aPaymentCreatedEvent();

        emittedEventDao.recordEmission(eventThatHasBeenEmitted);

        final List<Map<String, Object>> events = databaseTestHelper.readEmittedEvents();

        assertThat(events.size(), is(1));
        final Map<String, Object> event = events.get(0);
        assertThat(event.get("resource_external_id"), is(eventThatHasBeenEmitted.getResourceExternalId()));
    }

    @Test
    public void hasBeenEmittedBefore_shouldReturnFalseIfNoRecord() {
        final boolean b = emittedEventDao.hasBeenEmittedBefore(aPaymentCreatedEvent());

        final List<Map<String, Object>> events = databaseTestHelper.readEmittedEvents();
        assertThat(events.size(), is(0));

        assertThat(b, is(false));
    }

    @Test
    public void hasBeenEmittedBefore_shouldReturnTrueIfRecordedBefore() {
        final PaymentCreated paymentCreatedEvent = aPaymentCreatedEvent();

        emittedEventDao.recordEmission(paymentCreatedEvent);

        assertThat(emittedEventDao.hasBeenEmittedBefore(paymentCreatedEvent), is(true));
    }

    @Test
    public void recordEmissionWithParameters_shouldRecordEmission() {
        final PaymentCreated eventToRecord = aPaymentCreatedEvent();

        emittedEventDao.recordEmission(eventToRecord.getResourceType(), eventToRecord.getResourceExternalId(),
                eventToRecord.getEventType(), eventToRecord.getTimestamp());

        final List<Map<String, Object>> events = databaseTestHelper.readEmittedEvents();

        assertThat(events.size(), is(1));
        final Map<String, Object> event = events.get(0);
        assertThat(event.get("resource_external_id"), is(eventToRecord.getResourceExternalId()));
        assertThat(event.get("resource_type"), is(eventToRecord.getResourceType().getLowercase()));
        assertThat(event.get("event_type"), is(eventToRecord.getEventType()));
        assertThat(event.get("emitted_date"), is(nullValue()));
    }

    @Test
    public void markEventAsEmitted_shouldRecordEventAndEmittedDate() {
        final RefundSubmitted eventToRecord = aRefundSubmittedEvent(null);

        emittedEventDao.recordEmission(eventToRecord.getResourceType(), eventToRecord.getResourceExternalId(),
                eventToRecord.getEventType(), eventToRecord.getTimestamp());

        List<Map<String, Object>> events = databaseTestHelper.readEmittedEvents();
        Map<String, Object> event = events.get(0);
        assertThat(event.get("emitted_date"), is(nullValue()));
        assertThat(event.get("event_date"), is(nullValue()));

        final RefundSubmitted eventToUpdate = aRefundSubmittedEvent(ZonedDateTime.parse("2019-01-01T14:00:00Z"));
        emittedEventDao.markEventAsEmitted(eventToUpdate);
        events = databaseTestHelper.readEmittedEvents();
        event = events.get(0);
        assertThat(event.get("emitted_date"), is(notNullValue()));
        assertThat(event.get("event_date").toString(), is("2019-01-01 14:00:00.0"));
    }

    @Test
    public void markEventAsEmitted_shouldNotUpdateIfNoRecordIsFoundWithNullEmittedDate() {
        final PaymentCreated eventToRecord = aPaymentCreatedEvent();
        emittedEventDao.recordEmission(eventToRecord);

        List<Map<String, Object>> events = databaseTestHelper.readEmittedEvents();

        Map<String, Object> event = events.get(0);
        assertThat(event.get("emitted_date"), is(notNullValue()));
        String emittedDateBeforeUpdate = event.get("emitted_date").toString();

        emittedEventDao.markEventAsEmitted(eventToRecord);
        events = databaseTestHelper.readEmittedEvents();
        event = events.get(0);
        assertThat(event.get("emitted_date").toString(), is(emittedDateBeforeUpdate));
    }

    private PaymentCreated aPaymentCreatedEvent() {
        PaymentCreatedEventDetails eventDetails = new PaymentCreatedEventDetails(
                1L, "desc", "ref", "return_url",
                100L, "someProvider", "en", false, null);
        return new PaymentCreated("my-resource-external-id", eventDetails, ZonedDateTime.parse("2019-01-01T14:00:00Z"));
    }

    private RefundSubmitted aRefundSubmittedEvent(ZonedDateTime eventTimestamp) {
        return new RefundSubmitted("my-resource-external-id",
                "parent-external-id",
                null, eventTimestamp);
    }
}
