package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByUserEventDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.Instant;

public class RefundCreatedByUser extends RefundEvent {

    private RefundCreatedByUser(String serviceId, boolean live, String resourceExternalId, String parentResourceExternalId,
                                RefundCreatedByUserEventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, resourceExternalId, parentResourceExternalId, eventDetails, timestamp);
    }

    public static RefundCreatedByUser from(RefundHistory refundHistory, Charge charge) {
        return new RefundCreatedByUser(
                charge.getServiceId(),
                charge.isLive(),
                refundHistory.getExternalId(),
                refundHistory.getChargeExternalId(),
                new RefundCreatedByUserEventDetails(
                        refundHistory.getAmount(),
                        refundHistory.getUserExternalId(),
                        charge.getGatewayAccountId().toString(),
                        refundHistory.getUserEmail()),
                refundHistory.getHistoryStartDate().toInstant());
    }
}
