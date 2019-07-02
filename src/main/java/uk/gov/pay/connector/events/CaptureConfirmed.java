package uk.gov.pay.connector.events;

import java.time.ZonedDateTime;

/**
 *  Confirmed by notification from payment gateway 
 **/
public class CaptureConfirmed extends PaymentEvent {
    public CaptureConfirmed(String resourceExternalId, ZonedDateTime timestamp, ZonedDateTime gatewayEventDate) {
        super(resourceExternalId, new CaptureConfirmedEventDetails(gatewayEventDate), timestamp);
    }
}
