package uk.gov.pay.connector.events.dao;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.events.EmittedEventEntity;
import uk.gov.pay.connector.it.dao.DaoITestBase;

import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class EmittedEventDaoIT extends DaoITestBase {
    private EmittedEventDao emittedEventDao;

    @Before
    public void setUp() {
        emittedEventDao = env.getInstance(EmittedEventDao.class);
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

        assertThat((String) persisted.get("resource_type"), is("resource-type"));
        assertThat((String) persisted.get("resource_external_id"), is("external-id"));
        assertThat((String) persisted.get("event_type"), is("event-type"));
        final Timestamp eventDateInUTC = Timestamp.valueOf("2019-01-01 11:00:00");
        assertThat(persisted.get("event_date"), is(eventDateInUTC));
        assertThat(persisted.get("emitted_date"), is(Timestamp.valueOf("2019-01-01 13:00:00")));
    }
}
