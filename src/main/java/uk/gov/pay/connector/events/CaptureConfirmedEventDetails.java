package uk.gov.pay.connector.events;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.ZonedDateTime;

public class CaptureConfirmedEventDetails extends EventDetails {
    private final ZonedDateTime gatewayEventDate;

    public CaptureConfirmedEventDetails(ZonedDateTime gatewayEventDate) {
        this.gatewayEventDate = gatewayEventDate;
    }
}
