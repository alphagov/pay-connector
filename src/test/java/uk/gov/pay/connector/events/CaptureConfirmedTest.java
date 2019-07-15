package uk.gov.pay.connector.events;

import org.junit.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.CaptureConfirmedEventDetails;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CaptureConfirmedTest {

    private final ChargeEntity chargeEntity = ChargeEntityFixture
            .aValidChargeEntity()
            .build();

    @Test
    public void serializesGatewayEventTimeGivenChargeEvent() {
        ZonedDateTime gatewayEventTime = ZonedDateTime.now();

        ChargeEventEntity chargeEvent = mock(ChargeEventEntity.class);
        when(chargeEvent.getChargeEntity()).thenReturn(chargeEntity);
        when(chargeEvent.getGatewayEventDate()).thenReturn(Optional.of(gatewayEventTime));

        CaptureConfirmed captureConfirmed = CaptureConfirmed.from(chargeEvent);
        CaptureConfirmedEventDetails captureConfirmedEventDetails = (CaptureConfirmedEventDetails) captureConfirmed.getEventDetails();

        assertEquals(captureConfirmed.getResourceExternalId(), chargeEvent.getChargeEntity().getExternalId());
        assertEquals(captureConfirmedEventDetails.getGatewayEventDate(), gatewayEventTime);
    }


}
