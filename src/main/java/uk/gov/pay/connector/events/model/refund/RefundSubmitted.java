package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithGatewayTransactionIdDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.ZonedDateTime;

public class RefundSubmitted extends RefundEvent {

    public RefundSubmitted(String serviceId, boolean live, String resourceExternalId, String parentResourceExternalId,
                           RefundEventWithGatewayTransactionIdDetails referenceDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, parentResourceExternalId, referenceDetails, timestamp);
    }

    public static RefundSubmitted from(Charge charge, RefundHistory refundHistory) {
        return new RefundSubmitted(
                charge.getServiceId(),
                charge.isLive(),
                refundHistory.getExternalId(),
                refundHistory.getChargeExternalId(),
                new RefundEventWithGatewayTransactionIdDetails(refundHistory.getGatewayTransactionId()),
                refundHistory.getHistoryStartDate());
    }
}
