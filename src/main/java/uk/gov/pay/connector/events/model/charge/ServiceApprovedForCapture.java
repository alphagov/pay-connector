package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class ServiceApprovedForCapture extends PaymentEventWithoutDetails {
    public ServiceApprovedForCapture(String resourceExternalId, ZonedDateTime timestamp) {
        super(resourceExternalId, timestamp);
    }
}
