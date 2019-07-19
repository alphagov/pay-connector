package uk.gov.pay.connector.events.model.charge;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.EmptyEventDetails;
import uk.gov.pay.connector.events.eventdetails.charge.PaymentCreatedEventDetails;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.Is.is;

@RunWith(MockitoJUnitRunner.class)
public class PaymentEventFactoryTest {

    private final ChargeEntity charge = ChargeEntityFixture.aValidChargeEntity().build();

    @Mock
    private ChargeEventEntity chargeEvent;

    @Before
    public void setUp() {
        when(chargeEvent.getChargeEntity()).thenReturn(charge);
        when(chargeEvent.getUpdated()).thenReturn(ZonedDateTime.now());
    }

    @Test
    public void givenAPaymentCreatedTypeShouldCreateAPaymentCreatedEvent() {
        PaymentEvent event = PaymentEventFactory.create(PaymentCreated.class, chargeEvent);

        assertThat(event, instanceOf(PaymentCreated.class));
        assertThat(event.getEventDetails(), instanceOf(PaymentCreatedEventDetails.class));
        assertThat(event.getResourceExternalId(), is(charge.getExternalId()));
    }

    @Test
    public void givenANonPayloadPaymentEventTypeShouldCreateTheCorrectPaymentEventType() {
        PaymentEvent event = PaymentEventFactory.create(CaptureAbandonedAfterTooManyRetries.class, chargeEvent);

        assertThat(event, instanceOf(CaptureAbandonedAfterTooManyRetries.class));
        assertThat(event.getEventDetails(), instanceOf(EmptyEventDetails.class));
    }
}
