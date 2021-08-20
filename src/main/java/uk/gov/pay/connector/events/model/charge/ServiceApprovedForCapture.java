package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class ServiceApprovedForCapture extends PaymentEventWithoutDetails {
    public ServiceApprovedForCapture(String serviceId, boolean isLive, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, isLive, resourceExternalId, timestamp);
    }
}
