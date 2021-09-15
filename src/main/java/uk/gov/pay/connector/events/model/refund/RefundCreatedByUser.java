package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByUserEventDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.ZonedDateTime;

public class RefundCreatedByUser extends RefundEvent {

    private RefundCreatedByUser(String serviceId, boolean live, String resourceExternalId, String parentResourceExternalId, RefundCreatedByUserEventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, parentResourceExternalId, eventDetails, timestamp);
    }

    public static RefundCreatedByUser from(RefundHistory refundHistory, Long gatewayAccountId) {
        return new RefundCreatedByUser(
                refundHistory.getServiceId(),
                refundHistory.isLive(),
                refundHistory.getExternalId(),
                refundHistory.getChargeExternalId(),
                new RefundCreatedByUserEventDetails(
                        refundHistory.getAmount(),
                        refundHistory.getUserExternalId(),
                        gatewayAccountId.toString(),
                        refundHistory.getUserEmail()),
                refundHistory.getHistoryStartDate());
    }
}
