package uk.gov.pay.connector.events;

import org.junit.Test;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.CaptureConfirmedEventDetails;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.ChargeEntityFixture.aValidChargeEntity;

public class CaptureConfirmedTest {

    private final ChargeEntityFixture chargeEntity = aValidChargeEntity();

    @Test
    public void serializesEventDetailsGivenChargeEvent() {
        ZonedDateTime gatewayEventTime = ZonedDateTime.now();
        Long fee = 5L;
        Long netAmount = 495L;

        chargeEntity.withFee(fee);
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity.build());
        when(chargeEvent.getGatewayEventDate()).thenReturn(Optional.of(gatewayEventTime));

        CaptureConfirmed captureConfirmed = CaptureConfirmed.from(chargeEvent);
        CaptureConfirmedEventDetails captureConfirmedEventDetails = (CaptureConfirmedEventDetails) captureConfirmed.getEventDetails();

        assertEquals(captureConfirmed.getResourceExternalId(), chargeEvent.getChargeEntity().getExternalId());
        assertEquals(captureConfirmedEventDetails.getGatewayEventDate(), gatewayEventTime);
        assertEquals(captureConfirmedEventDetails.getFee(), fee);
        assertEquals(captureConfirmedEventDetails.getNetAmount(), netAmount);
    }

    @Test
    public void serializesEventGivenNoDetailValues() {
        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity.build());
        when(chargeEvent.getGatewayEventDate()).thenReturn(Optional.empty());

        CaptureConfirmed captureConfirmed = CaptureConfirmed.from(chargeEvent);
        CaptureConfirmedEventDetails captureConfirmedEventDetails = (CaptureConfirmedEventDetails) captureConfirmed.getEventDetails();

        assertEquals(captureConfirmed.getResourceExternalId(), chargeEvent.getChargeEntity().getExternalId());
        assertNull(captureConfirmedEventDetails.getGatewayEventDate());
        assertNull(captureConfirmedEventDetails.getFee());
        assertNull(captureConfirmedEventDetails.getNetAmount());
    }

}
