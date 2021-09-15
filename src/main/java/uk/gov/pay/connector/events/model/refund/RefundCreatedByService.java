package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByServiceEventDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.ZonedDateTime;

public class RefundCreatedByService extends RefundEvent {

    private RefundCreatedByService(String serviceId, boolean live, String resourceExternalId, String parentResourceExternalId, RefundCreatedByServiceEventDetails eventDetails, ZonedDateTime timestamp) {
        super(serviceId, live, resourceExternalId, parentResourceExternalId, eventDetails, timestamp);
    }

    public static RefundCreatedByService from(RefundHistory refundHistory, Charge charge) {
        return new RefundCreatedByService(
                charge.getServiceId(),
                charge.isLive(),
                refundHistory.getExternalId(),
                refundHistory.getChargeExternalId(),
                new RefundCreatedByServiceEventDetails(
                        refundHistory.getAmount(),
                        charge.getGatewayAccountId().toString()),
                refundHistory.getHistoryStartDate()
        );

    }
}
