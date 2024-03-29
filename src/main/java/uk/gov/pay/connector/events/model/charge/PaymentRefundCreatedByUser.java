package uk.gov.pay.connector.events.model.charge;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.time.Instant;

public class PaymentRefundCreatedByUser extends PaymentEvent {
    public PaymentRefundCreatedByUser(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, EventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, eventDetails, timestamp);
    }
}
