package uk.gov.pay.connector.events.eventdetails.charge;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.service.payments.commons.api.json.IsoInstantMicrosecondSerializer;

import java.time.Instant;
import java.time.ZonedDateTime;

public class CaptureConfirmedEventDetails extends EventDetails {
    @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
    private final Instant gatewayEventDate;
    @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
    private final Instant capturedDate;
    private final Long fee;
    private final Long netAmount;


    public CaptureConfirmedEventDetails(Instant gatewayEventDate, Instant capturedDate, Long fee, Long netAmount) {
        this.gatewayEventDate = gatewayEventDate;
        this.capturedDate = capturedDate;
        this.fee = fee;
        this.netAmount = netAmount;
    }

    public static CaptureConfirmedEventDetails from(ChargeEventEntity chargeEvent) {
        if (chargeEvent.getChargeEntity().getFees().size() > 1) {
            return new CaptureConfirmedEventDetails(
                    chargeEvent.getGatewayEventDate().map(ZonedDateTime::toInstant).orElse(null),
                    chargeEvent.getUpdated().toInstant(),
                    null,
                    null
            );
        }
        return new CaptureConfirmedEventDetails(
                chargeEvent.getGatewayEventDate().map(ZonedDateTime::toInstant).orElse(null),
                chargeEvent.getUpdated().toInstant(),
                chargeEvent.getChargeEntity().getFeeAmount().orElse(null),
                chargeEvent.getChargeEntity().getNetAmount().orElse(null)
        );
    }

    public Instant getCapturedDate() {
        return capturedDate;
    }

    public Instant getGatewayEventDate() {
        return gatewayEventDate;
    }

    public Long getFee() {
        return fee;
    }

    public Long getNetAmount() {
        return netAmount;
    }
}
