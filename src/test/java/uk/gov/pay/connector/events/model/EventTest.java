package uk.gov.pay.connector.events.model;

import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.events.model.charge.GatewayRequires3dsAuthorisation;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventTest {

    @Test
    void eventTypeForClass_returnsScreamingSnakeCase() {
        assertEquals("EVENT_TEST", Event.eventTypeForClass(EventTest.class));
    }

    @Test
    void eventTypeForClass_shouldAlsoConsiderNumbers() {
        assertEquals("GATEWAY_REQUIRES_3DS_AUTHORISATION", 
                Event.eventTypeForClass(GatewayRequires3dsAuthorisation.class));
    }
}
