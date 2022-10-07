package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithGatewayTransactionIdDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.Instant;

public class RefundError extends RefundEvent {

    public RefundError(String serviceId, boolean live, String resourceExternalId, String parentResourceExternalId,
                       RefundEventWithGatewayTransactionIdDetails referenceDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, parentResourceExternalId, referenceDetails, timestamp);
    }

    public RefundError from(RefundHistory refundHistory, Charge charge) {
        return new RefundError(
                charge.getServiceId(),
                charge.isLive(),
                refundHistory.getExternalId(),
                refundHistory.getChargeExternalId(),
                new RefundEventWithGatewayTransactionIdDetails(refundHistory.getGatewayTransactionId()),
                refundHistory.getHistoryStartDate().toInstant());
    }
}
