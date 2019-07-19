package uk.gov.pay.connector.events.model.charge;

import org.junit.Test;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;

import static org.junit.Assert.assertEquals;

public class PaymentEventTest {

    @Test
    public void eventTypeForClass_returnsScreamingSnakeCase() {
        assertEquals("PAYMENT_EVENT_TEST", PaymentEvent.eventTypeForClass(PaymentEventTest.class));
    }
}
