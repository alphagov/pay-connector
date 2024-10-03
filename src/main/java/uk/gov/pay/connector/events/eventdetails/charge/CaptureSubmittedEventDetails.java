package uk.gov.pay.connector.events.eventdetails.charge;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.service.payments.commons.api.json.IsoInstantMicrosecondSerializer;

import java.time.Instant;

public class CaptureSubmittedEventDetails extends EventDetails {
    @JsonSerialize(using = IsoInstantMicrosecondSerializer.class)
    private final Instant captureSubmittedDate;

    private CaptureSubmittedEventDetails(Instant captureSubmittedDate) {
        this.captureSubmittedDate = captureSubmittedDate;
    }

    public static CaptureSubmittedEventDetails from(ChargeEventEntity chargeEvent) {
        return new CaptureSubmittedEventDetails(chargeEvent.getUpdated().toInstant());
    }

    public Instant getCaptureSubmittedDate() {
        return captureSubmittedDate;
    }
}
