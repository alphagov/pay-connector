package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithGatewayTransactionIdDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.ZonedDateTime;

public class RefundError extends RefundEvent {

    public RefundError(String serviceId, boolean live, String resourceExternalId, String parentResourceExternalId,
                       RefundEventWithGatewayTransactionIdDetails referenceDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, parentResourceExternalId, referenceDetails, timestamp);
    }

    public RefundError from(RefundHistory refundHistory) {
        return new RefundError(
                refundHistory.getServiceId(),
                refundHistory.isLive(),
                refundHistory.getExternalId(),
                refundHistory.getChargeExternalId(),
                new RefundEventWithGatewayTransactionIdDetails(refundHistory.getGatewayTransactionId()),
                refundHistory.getHistoryStartDate());
    }
}
