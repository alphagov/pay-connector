package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithGatewayTransactionIdDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.Instant;

public class RefundSucceeded extends RefundEvent {

    public RefundSucceeded(String serviceId, boolean live, Long gatewayAccountInternalId, String resourceExternalId, String parentResourceExternalId,
                           RefundEventWithGatewayTransactionIdDetails referenceDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountInternalId, resourceExternalId, parentResourceExternalId, referenceDetails, timestamp);
    }

    public static RefundSucceeded from(Charge charge, RefundHistory refundHistory) {
        return new RefundSucceeded(charge.getServiceId(),
                charge.isLive(),
                charge.getGatewayAccountId(),
                refundHistory.getExternalId(),
                refundHistory.getChargeExternalId(),
                new RefundEventWithGatewayTransactionIdDetails(refundHistory.getGatewayTransactionId()),
                refundHistory.getHistoryStartDate().toInstant());
    }
}
