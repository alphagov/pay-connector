package uk.gov.pay.connector.events.eventdetails.charge;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.events.MicrosecondPrecisionDateTimeSerializer;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

public class CaptureSubmittedEventDetails extends EventDetails {
    @JsonSerialize(using = MicrosecondPrecisionDateTimeSerializer.class)
    private final ZonedDateTime captureSubmittedDate;

    private CaptureSubmittedEventDetails(ZonedDateTime captureSubmittedDate) {
        this.captureSubmittedDate = captureSubmittedDate;
    }

    public static CaptureSubmittedEventDetails from(ChargeEventEntity chargeEvent) {
        return new CaptureSubmittedEventDetails(chargeEvent.getUpdated());
    }

    public ZonedDateTime getCaptureSubmittedDate() {
        return captureSubmittedDate;
    }
}
