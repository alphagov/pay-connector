package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class BackFillerGatewayTransactionIdSetEventDetails extends EventDetails {
    private final String gatewayTransactionId;

    public BackFillerGatewayTransactionIdSetEventDetails(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public static BackFillerGatewayTransactionIdSetEventDetails from(ChargeEntity chargeEntity) {
        return new BackFillerGatewayTransactionIdSetEventDetails(chargeEntity.getGatewayTransactionId());
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }
}
