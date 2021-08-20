package uk.gov.pay.connector.events.model.charge;

import java.time.ZonedDateTime;

public class ServiceApprovedForCapture extends PaymentEventWithoutDetails {
    public ServiceApprovedForCapture(String serviceId, boolean live, String resourceExternalId, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, timestamp);
    }
}
