package uk.gov.pay.connector.events.eventdetails;

import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;

import java.time.ZonedDateTime;

public class CaptureConfirmedEventDetails extends EventDetails {
    private final ZonedDateTime gatewayEventDate;

    private CaptureConfirmedEventDetails(ZonedDateTime gatewayEventDate) {
        this.gatewayEventDate = gatewayEventDate;
    }

    public static CaptureConfirmedEventDetails from(ChargeEventEntity chargeEvent) {
        return new CaptureConfirmedEventDetails(
                chargeEvent.getGatewayEventDate().orElse(null)
        );
    }

    public ZonedDateTime getGatewayEventDate() {
        return gatewayEventDate;
    }
}
