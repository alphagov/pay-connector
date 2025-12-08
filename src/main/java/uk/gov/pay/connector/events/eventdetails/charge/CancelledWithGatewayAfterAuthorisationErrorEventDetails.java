package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class CancelledWithGatewayAfterAuthorisationErrorEventDetails extends EventDetails {

    private String gatewayTransactionId;

    public CancelledWithGatewayAfterAuthorisationErrorEventDetails(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public static CancelledWithGatewayAfterAuthorisationErrorEventDetails from(ChargeEntity charge) {
        return new CancelledWithGatewayAfterAuthorisationErrorEventDetails(charge.getGatewayTransactionId());
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }
}
