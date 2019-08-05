package uk.gov.pay.connector.refund.service;

import uk.gov.pay.connector.events.model.refund.RefundCreatedByService;
import uk.gov.pay.connector.events.model.refund.RefundCreatedByUser;
import uk.gov.pay.connector.events.model.refund.RefundError;
import uk.gov.pay.connector.events.model.refund.RefundSubmitted;
import uk.gov.pay.connector.events.model.refund.RefundSucceeded;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

public class RefundStateEventMap {

        static Class calculateRefundEventClass(String userExternalId, RefundStatus refundStatus) {
        switch (refundStatus) {
            case CREATED:
                if (userExternalId != null) {
                    return RefundCreatedByUser.class;
                } else {
                    return RefundCreatedByService.class;
                }
            case REFUND_SUBMITTED:
                return RefundSubmitted.class;
            case REFUNDED:
                return RefundSucceeded.class;
            case REFUND_ERROR:
                return RefundError.class;
            default:
                throw new IllegalArgumentException("Unexpected refund state transition");
        }
    }
}
