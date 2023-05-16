package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithGatewayTransactionIdDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.Instant;

public class RefundSubmitted extends RefundEvent {

    public RefundSubmitted(String serviceId, boolean live, Long gatewayAccountId, String resourceExternalId, String parentResourceExternalId,
                           RefundEventWithGatewayTransactionIdDetails referenceDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountId, resourceExternalId, parentResourceExternalId, referenceDetails, timestamp);
    }

    public static RefundSubmitted from(Charge charge, RefundHistory refundHistory) {
        return new RefundSubmitted(
                charge.getServiceId(),
                charge.isLive(),
                charge.getGatewayAccountId(),
                refundHistory.getExternalId(),
                refundHistory.getChargeExternalId(),
                new RefundEventWithGatewayTransactionIdDetails(refundHistory.getGatewayTransactionId()),
                refundHistory.getHistoryStartDate().toInstant());
    }
}
