package uk.gov.pay.connector.events.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventTest {

    @Test
    public void eventTypeForClass_returnsScreamingSnakeCase() {
        assertEquals("EVENT_TEST", Event.eventTypeForClass(EventTest.class));
    }
}
