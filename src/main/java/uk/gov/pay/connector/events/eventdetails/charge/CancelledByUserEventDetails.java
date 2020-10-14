package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class CancelledByUserEventDetails extends EventDetails {

    private String gatewayTransactionId;

    public CancelledByUserEventDetails(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public static CancelledByUserEventDetails from(ChargeEntity charge) {
        return new CancelledByUserEventDetails(charge.getGatewayTransactionId());
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }
}
