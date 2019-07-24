package uk.gov.pay.connector.events.model;

import org.junit.Test;
import uk.gov.pay.connector.events.model.charge.GatewayRequires3dsAuthorisation;

import static org.junit.Assert.assertEquals;

public class EventTest {

    @Test
    public void eventTypeForClass_returnsScreamingSnakeCase() {
        assertEquals("EVENT_TEST", Event.eventTypeForClass(EventTest.class));
    }

    @Test
    public void eventTypeForClass_shouldAlsoConsiderNumbers() {
        assertEquals("GATEWAY_REQUIRES_3DS_AUTHORISATION", 
                Event.eventTypeForClass(GatewayRequires3dsAuthorisation.class));
    }
}
