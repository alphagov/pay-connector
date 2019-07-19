package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

public class CaptureConfirmedEventDetails extends EventDetails {
    private final ZonedDateTime gatewayEventDate;
    private final Long fee;
    private final Long netAmount;

    private CaptureConfirmedEventDetails(ZonedDateTime gatewayEventDate, Long fee, Long netAmount) {
        this.gatewayEventDate = gatewayEventDate;
        this.fee = fee;
        this.netAmount = netAmount;
    }

    public static CaptureConfirmedEventDetails from(ChargeEventEntity chargeEvent) {
        return new CaptureConfirmedEventDetails(
                chargeEvent.getGatewayEventDate().orElse(null),
                chargeEvent.getChargeEntity().getFeeAmount().orElse(null),
                chargeEvent.getChargeEntity().getNetAmount().orElse(null)
        );
    }

    public ZonedDateTime getGatewayEventDate() {
        return gatewayEventDate;
    }

    public Long getFee() {
        return fee;
    }

    public Long getNetAmount() {
        return netAmount;
    }
}
