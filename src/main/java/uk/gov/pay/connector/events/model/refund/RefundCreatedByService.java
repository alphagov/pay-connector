package uk.gov.pay.connector.events.model.refund;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.events.eventdetails.refund.RefundCreatedByServiceEventDetails;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;

import java.time.Instant;

public class RefundCreatedByService extends RefundEvent {

    private RefundCreatedByService(String serviceId, boolean live, Long gatewayAccountInternalId, String resourceExternalId, String parentResourceExternalId,
                                   RefundCreatedByServiceEventDetails eventDetails, Instant timestamp) {
        super(serviceId, live, gatewayAccountInternalId, resourceExternalId, parentResourceExternalId, eventDetails, timestamp);
    }

    public static RefundCreatedByService from(RefundHistory refundHistory, Charge charge) {
        return new RefundCreatedByService(
                charge.getServiceId(),
                charge.isLive(),
                charge.getGatewayAccountId(),
                refundHistory.getExternalId(),
                refundHistory.getChargeExternalId(),
                new RefundCreatedByServiceEventDetails(
                        refundHistory.getAmount(),
                        charge.getGatewayAccountId().toString()),
                refundHistory.getHistoryStartDate().toInstant()
        );

    }
}
